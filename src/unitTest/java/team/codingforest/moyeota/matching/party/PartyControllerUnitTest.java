package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.dto.MatchCandidate;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;
import team.codingforest.moyeota.matching.party.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.party.dto.OpenReservationCommand;
import team.codingforest.moyeota.matching.party.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.party.dto.PartyResult;
import team.codingforest.moyeota.matching.party.dto.PartyStatusResponse;
import team.codingforest.moyeota.matching.party.dto.RealTimeMatchingCommand;
import team.codingforest.moyeota.user.api.MemberSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartyController.class)
class PartyControllerUnitTest extends ControllerUnitTestSupport {
    private static final Long PARTY_ID = 100L;
    private static final UUID MEMBER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00Z");
    private static final String INVALID_REQUEST = "INVALID_REQUEST";
    private static final String UNAUTHORIZED_CODE = "USER005";

    private static final String OPEN_BODY = """
            {
              "departureLat": 37.4979,
              "departureLng": 127.0276,
              "destinationLat": 37.3948,
              "destinationLng": 127.1112,
              "departure": "강남역",
              "destination": "판교역",
              "capacity": 3
            }
            """;
    private static final String REALTIME_BODY = """
            {
              "departureLat": 37.4979,
              "departureLng": 127.0276,
              "destLat": 37.3948,
              "destLng": 127.1112
            }
            """;
    private static final OpenPartyCommand OPEN_COMMAND = new OpenPartyCommand(USER_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 3);
    private static final RealTimeMatchingCommand REALTIME_COMMAND = new RealTimeMatchingCommand(37.4979, 127.0276, 37.3948, 127.1112);

    @MockitoBean
    private PartyService partyService;

    // ───────────────────────── 예약 방 ─────────────────────────

    private static final java.time.Instant DEPARTURE_AT = java.time.Instant.parse("2026-11-03T08:00:00Z");
    private static final Instant CONFIRMED_AT = Instant.parse("2026-11-03T08:30:00Z");

    private static String reservationBody(int capacity, String departureAtField) {
        return """
                {
                  "departureLat": 37.4979,
                  "departureLng": 127.0276,
                  "destinationLat": 37.3948,
                  "destinationLng": 127.1112,
                  "departure": "강남역",
                  "destination": "판교역",
                  "capacity": %d%s
                }
                """.formatted(capacity, departureAtField);
    }

    @DisplayName("POST /api/v1/matching/reservations - 토큰의 사용자로 예약 방을 만들고 출발 시각을 돌려준다")
    @Test
    void reserve() throws Exception {
        OpenReservationCommand command = new OpenReservationCommand(USER_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 3, DEPARTURE_AT);
        PartyResult result = new PartyResult(PARTY_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 3, 1, 500, 500, "ACTIVE", CREATED_AT, null, null, null, "RESERVED", DEPARTURE_AT);
        doReturn(result).when(partyService).reserve(eq(command));

        mockMvc.perform(post("/api/v1/matching/reservations").with(loginUser()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(3, ", \"departureAt\": \"2026-11-03T08:00:00Z\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PARTY_ID))
                .andExpect(jsonPath("$.roomType").value("RESERVED"))
                .andExpect(jsonPath("$.departureAt").value("2026-11-03T08:00:00Z"))
                .andExpect(jsonPath("$.estimateFare").value(nullValue()))
                .andExpect(jsonPath("$.route").value(nullValue()));
    }

    @DisplayName("POST /api/v1/matching/reservations - 기사 기능은 없앴지만 출시된 앱이 읽는 taxiDriverId 키는 null 로 남는다")
    @Test
    void reserve_taxiDriverIdKeyKeptAsNull() throws Exception {

        /*
         * settings
         */
        OpenReservationCommand command = new OpenReservationCommand(USER_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 3, DEPARTURE_AT);
        PartyResult result = new PartyResult(PARTY_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 3, 1, 500, 500, "ACTIVE", CREATED_AT, null, null, null, "RESERVED", DEPARTURE_AT);

        doReturn(result).when(partyService).reserve(eq(command));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/reservations").with(loginUser()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(3, ", \"departureAt\": \"2026-11-03T08:00:00Z\"")))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("taxiDriverId")))
                .andExpect(jsonPath("$.taxiDriverId").value(nullValue()));
    }

    @DisplayName("POST /api/v1/matching/reservations - 출발 시각이 없으면 400")
    @Test
    void reserve_withoutDepartureAt() throws Exception {
        mockMvc.perform(post("/api/v1/matching/reservations").with(loginUser()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(3, "")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/reservations - 정원이 1명이면 400")
    @Test
    void reserve_soloCapacity() throws Exception {
        mockMvc.perform(post("/api/v1/matching/reservations").with(loginUser()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(1, ", \"departureAt\": \"2026-11-03T08:00:00Z\"")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/reservations - 시간이 겹치면 409 PARTY_TIME_CONFLICT")
    @Test
    void reserve_timeConflict() throws Exception {
        doThrow(new BusinessException(MatchingErrorCode.PARTY_TIME_CONFLICT)).when(partyService).reserve(any());

        mockMvc.perform(post("/api/v1/matching/reservations").with(loginUser()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(3, ", \"departureAt\": \"2026-11-03T08:00:00Z\"")))
                .andExpect(status().isConflict());
    }

    @DisplayName("GET /api/v1/matching/reservations - 기간 안의 예약 방을 돌려준다")
    @Test
    void listReservations() throws Exception {
        java.time.Instant from = java.time.Instant.parse("2026-11-03T00:00:00Z");
        java.time.Instant to = java.time.Instant.parse("2026-11-04T00:00:00Z");
        doReturn(List.of(new ReservationSummary(PARTY_ID, "강남역", "판교역", 2L, 3, PartyStatus.ACTIVE, 37.4979, 127.0276, 37.3948, 127.1112, DEPARTURE_AT)))
                .when(partyService).findReservations(eq(from), eq(to));

        mockMvc.perform(get("/api/v1/matching/reservations").with(loginUser())
                        .param("from", "2026-11-03T00:00:00Z").param("to", "2026-11-04T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list", hasSize(1)))
                .andExpect(jsonPath("$.list[0].partyId").value(PARTY_ID))
                .andExpect(jsonPath("$.list[0].currentMembers").value(2))
                .andExpect(jsonPath("$.list[0].destinationLat").value(37.3948))
                .andExpect(jsonPath("$.list[0].departureAt").value("2026-11-03T08:00:00Z"));
    }

    @DisplayName("GET /api/v1/matching/reservations/soon - 영역 안의 곧 출발 방을 돌려준다")
    @Test
    void listSoonReservations() throws Exception {
        doReturn(List.of(new ReservationSummary(PARTY_ID, "강남역", "판교역", 1L, 3, PartyStatus.ACTIVE, 37.4979, 127.0276, 37.3948, 127.1112, DEPARTURE_AT)))
                .when(partyService).findSoonReservationsWithin(37.0, 126.0, 38.0, 128.0);

        mockMvc.perform(get("/api/v1/matching/reservations/soon").with(loginUser())
                        .param("swLat", "37.0").param("swLng", "126.0").param("neLat", "38.0").param("neLng", "128.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list[0].partyId").value(PARTY_ID));
    }

    // ───────────────────────── POST /matching/rooms ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms - 토큰의 사용자로 방을 만들고 방 정보를 돌려준다")
    @Test
    void open() throws Exception {

        /*
         * settings
         */
        doReturn(partyResult()).when(partyService).open(eq(OPEN_COMMAND));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PARTY_ID))
                .andExpect(jsonPath("$.departureLat").value(37.4979))
                .andExpect(jsonPath("$.departureLng").value(127.0276))
                .andExpect(jsonPath("$.destinationLat").value(37.3948))
                .andExpect(jsonPath("$.destinationLng").value(127.1112))
                .andExpect(jsonPath("$.departure").value("강남역"))
                .andExpect(jsonPath("$.destination").value("판교역"))
                .andExpect(jsonPath("$.capacity").value(3))
                .andExpect(jsonPath("$.currentMembers").value(1))
                .andExpect(jsonPath("$.departureRadius").value(500))
                .andExpect(jsonPath("$.destinationRadius").value(500))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.estimateFare").value(12000))
                .andExpect(jsonPath("$.estimateTime").value(25))
                .andExpect(jsonPath("$.route").value("encoded-path"))
                .andExpect(jsonPath("$.taxiDriverId").value(nullValue()));
        verify(partyService).open(eq(OPEN_COMMAND));
    }

    @DisplayName("POST /api/v1/matching/rooms - 기사 기능은 없앴지만 출시된 앱이 읽는 taxiDriverId 키는 null 로 남는다")
    @Test
    void open_taxiDriverIdKeyKeptAsNull() throws Exception {

        /*
         * settings
         */
        doReturn(partyResult()).when(partyService).open(eq(OPEN_COMMAND));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("taxiDriverId")))
                .andExpect(jsonPath("$.taxiDriverId").value(nullValue()));
    }

    @DisplayName("POST /api/v1/matching/rooms - 본문의 creatorId 는 무시하고 토큰의 사용자를 쓴다")
    @Test
    void open_ignoresBodyCreatorId() throws Exception {

        /*
         * settings
         */
        final String body = """
                {
                  "creatorId": 777,
                  "departureLat": 37.4979,
                  "departureLng": 127.0276,
                  "destinationLat": 37.3948,
                  "destinationLng": 127.1112,
                  "departure": "강남역",
                  "destination": "판교역",
                  "capacity": 3
                }
                """;

        doReturn(partyResult()).when(partyService).open(eq(OPEN_COMMAND));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isOk());
        verify(partyService).open(eq(OPEN_COMMAND));
    }

    @DisplayName("POST /api/v1/matching/rooms - 옛 앱이 보내는 모르는 필드는 무시한다")
    @Test
    void open_ignoresUnknownFields() throws Exception {

        /*
         * settings
         */
        final String body = """
                {
                  "departureLat": 37.4979,
                  "departureLng": 127.0276,
                  "destinationLat": 37.3948,
                  "destinationLng": 127.1112,
                  "departure": "강남역",
                  "destination": "판교역",
                  "capacity": 3,
                  "departureRadius": 100,
                  "destinationRadius": 100
                }
                """;

        doReturn(partyResult()).when(partyService).open(eq(OPEN_COMMAND));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isOk());
        verify(partyService).open(eq(OPEN_COMMAND));
    }

    @DisplayName("POST /api/v1/matching/rooms - 토큰이 없으면 401")
    @Test
    void open_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms - 본문이 없으면 400")
    @Test
    void open_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms - JSON 이 깨졌으면 400")
    @Test
    void open_malformedJson() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content("{\"departureLat\": "))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms - 필드 타입이 틀리면 400")
    @Test
    void open_wrongFieldType() throws Exception {

        /*
         * settings
         */
        final String body = """
                {
                  "departureLat": 37.4979,
                  "departureLng": 127.0276,
                  "destinationLat": 37.3948,
                  "destinationLng": 127.1112,
                  "departure": "강남역",
                  "destination": "판교역",
                  "capacity": "abc"
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms - 필수 필드가 null 이면 400 이고 서비스를 부르지 않는다")
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "departureLat, 출발지 위도는 필수입니다",
            "departureLng, 출발지 경도는 필수입니다",
            "destinationLat, 도착지 위도는 필수입니다",
            "destinationLng, 도착지 경도는 필수입니다",
            "departure, 출발지 이름은 필수입니다",
            "destination, 도착지 이름은 필수입니다",
            "capacity, 정원은 필수입니다"
    })
    void open_nullField(String field, String message) throws Exception {

        /*
         * settings
         */
        final String body = OPEN_BODY.replaceFirst("\"" + field + "\": [^,\\n]+", "\"" + field + "\": null");

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST))
                .andExpect(jsonPath("$.message").value(field + ": " + message));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms - 본문이 빈 객체면 400 이고 서비스를 부르지 않는다")
    @Test
    void open_emptyObject() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content("{}"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms - 출발지·도착지 이름이 공백이면 400 이고 서비스를 부르지 않는다")
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "departure, 강남역, 출발지 이름은 필수입니다",
            "destination, 판교역, 도착지 이름은 필수입니다"
    })
    void open_blankName(String field, String name, String message) throws Exception {

        /*
         * settings
         */
        final String body = OPEN_BODY.replace(name, "   ");

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST))
                .andExpect(jsonPath("$.message").value(field + ": " + message));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms - 정원이 1~3 밖이면 400 이고 서비스를 부르지 않는다")
    @ParameterizedTest(name = "capacity={0}")
    @CsvSource({
            "0, 정원은 1명 이상입니다",
            "-1, 정원은 1명 이상입니다",
            "4, 정원은 3명 이하입니다"
    })
    void open_capacityOutOfRange(int capacity, String message) throws Exception {

        /*
         * settings
         */
        final String body = OPEN_BODY.replace("\"capacity\": 3", "\"capacity\": " + capacity);

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST))
                .andExpect(jsonPath("$.message").value("capacity: " + message));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms - 정원 경계값 1과 3은 서비스까지 간다")
    @ParameterizedTest(name = "capacity={0}")
    @CsvSource({"1", "3"})
    void open_capacityBoundary(int capacity) throws Exception {

        /*
         * settings
         */
        final String body = OPEN_BODY.replace("\"capacity\": 3", "\"capacity\": " + capacity);
        final OpenPartyCommand command = new OpenPartyCommand(USER_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", capacity);

        doReturn(partyResult()).when(partyService).open(eq(command));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isOk());
        verify(partyService).open(eq(command));
    }

    @DisplayName("POST /api/v1/matching/rooms - Content-Type 이 JSON 이 아니면 415")
    @Test
    void open_unsupportedContentType() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.TEXT_PLAIN).content(OPEN_BODY))

        /*
         * verify
         */
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms - 서비스의 비즈니스 예외를 상태코드와 오류 코드로 바꾼다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "SAME_DEPARTURE_DESTINATION, 400",
            "INVALID_CAPACITY, 400",
            "OUT_OF_SERVICE_AREA, 400",
            "INVALID_ROUTE_ESTIMATE, 400",
            "ALREADY_JOINED_OTHER_PARTY, 409",
            "ROUTE_SEARCH_FAILED, 502"
    })
    void open_businessException(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(partyService).open(eq(OPEN_COMMAND));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }

    // ───────────────────────── POST /matching/rooms/{partyId}/join ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/join - 토큰의 사용자로 참여하고 상태와 인원을 돌려준다")
    @Test
    void join() throws Exception {

        /*
         * settings
         */
        doReturn(new PartyStatusResponse("COMPLETED", 3)).when(partyService).join(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/join", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.currentMembers").value(3));
        verify(partyService).join(eq(PARTY_ID), eq(USER_ID));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/join - 토큰이 없으면 401")
    @Test
    void join_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/join", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/join - partyId 가 숫자가 아니면 400")
    @Test
    void join_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/join", "abc").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/join - 서비스의 비즈니스 예외를 상태코드와 오류 코드로 바꾼다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "PARTY_NOT_FOUND, 404",
            "PARTY_FULL, 409",
            "PARTY_CLOSED, 409",
            "ALREADY_JOINED_PARTY, 409",
            "ALREADY_JOINED_OTHER_PARTY, 409"
    })
    void join_businessException(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(partyService).join(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/join", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    // ───────────────────────── DELETE /matching/leave/{partyId} ─────────────────────────

    @DisplayName("DELETE /api/v1/matching/leave/{partyId} - 토큰의 사용자가 나가고 본문 없이 204")
    @Test
    void leave() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/matching/leave/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(partyService).leave(eq(PARTY_ID), eq(USER_ID));
    }

    @DisplayName("DELETE /api/v1/matching/leave/{partyId} - 토큰이 없으면 401")
    @Test
    void leave_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/matching/leave/{partyId}", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    @DisplayName("DELETE /api/v1/matching/leave/{partyId} - partyId 가 숫자가 아니면 400")
    @Test
    void leave_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/matching/leave/{partyId}", "abc").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("DELETE /api/v1/matching/leave/{partyId} - 서비스의 비즈니스 예외를 상태코드와 오류 코드로 바꾼다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "NOT_PARTY_MEMBER, 403",
            "PARTY_NOT_FOUND, 404",
            "PARTY_NOT_RECRUITING, 409",
            "CANNOT_LEAVE_CONFIRMED_PARTY, 409"
    })
    void leave_businessException(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(partyService).leave(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/matching/leave/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    // ───────────────────────── GET /matching/rooms/{partyId} ─────────────────────────

    @DisplayName("GET /api/v1/matching/rooms/{partyId} - 방 상세와 멤버 목록을 돌려준다")
    @Test
    void detail() throws Exception {

        /*
         * settings
         */
        PartyDetailResult detail = new PartyDetailResult(PARTY_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 3, 2, 500, 500, "ACTIVE", CREATED_AT,
                List.of(new PartyDetailResult.MemberInfo(PUBLIC_ID, "방장", "host.png", null, 4, true, false),
                        new PartyDetailResult.MemberInfo(MEMBER_PUBLIC_ID, "참가자", "member.png", null, 0, false, false)),
                12000, 25, "encoded-path", null, 7L, "REALTIME", null, false);

        doReturn(detail).when(partyService).getPartyDetail(eq(PARTY_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PARTY_ID))
                .andExpect(jsonPath("$.departure").value("강남역"))
                .andExpect(jsonPath("$.destination").value("판교역"))
                .andExpect(jsonPath("$.capacity").value(3))
                .andExpect(jsonPath("$.currentMembers").value(2))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.estimateFare").value(12000))
                .andExpect(jsonPath("$.estimateTime").value(25))
                .andExpect(jsonPath("$.route").value("encoded-path"))
                .andExpect(jsonPath("$", hasKey("taxiDriverId")))
                .andExpect(jsonPath("$.taxiDriverId").value(nullValue()))
                .andExpect(jsonPath("$.version").value(7))
                .andExpect(jsonPath("$.roomType").value("REALTIME"))
                .andExpect(jsonPath("$.departureAt").value(nullValue()))
                .andExpect(jsonPath("$.confirmed").value(false))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0].publicId").value(PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.members[0].nickname").value("방장"))
                .andExpect(jsonPath("$.members[0].imageUrl").value("host.png"))
                .andExpect(jsonPath("$.members[0].rideCount").value(4))
                .andExpect(jsonPath("$.members[0].host").value(true))
                .andExpect(jsonPath("$.members[0].arrived").value(false))
                .andExpect(jsonPath("$.members[1].publicId").value(MEMBER_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.members[1].host").value(false));
        verify(partyService).getPartyDetail(eq(PARTY_ID));
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId} - 약속을 확정한 방은 약속 시간과 confirmed=true, 멤버별 도착 여부를 돌려준다")
    @Test
    void detail_confirmed() throws Exception {

        /*
         * settings
         */
        PartyDetailResult detail = new PartyDetailResult(PARTY_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 3, 2, 500, 500, "COMPLETED", CREATED_AT,
                List.of(new PartyDetailResult.MemberInfo(PUBLIC_ID, "방장", "host.png", null, 4, true, false),
                        new PartyDetailResult.MemberInfo(MEMBER_PUBLIC_ID, "참가자", "member.png", null, 0, false, true)),
                12000, 25, "encoded-path", null, 3L, "REALTIME", CONFIRMED_AT, true);

        doReturn(detail).when(partyService).getPartyDetail(eq(PARTY_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.departureAt").value("2026-11-03T08:30:00Z"))
                .andExpect(jsonPath("$.confirmed").value(true))
                .andExpect(jsonPath("$.members[0].badgeId").value(nullValue()))
                .andExpect(jsonPath("$.members[0].arrived").value(false))
                .andExpect(jsonPath("$.members[1].arrived").value(true));
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId} - 뱃지는 없앴지만 출시된 앱이 읽는 members[].badgeId 키는 null 로 남는다")
    @Test
    void detail_badgeIdKeyKeptAsNull() throws Exception {

        /*
         * settings
         */
        // 서비스가 만드는 그대로 MemberInfo.of 로 멤버를 채운다 - 사용자 요약에는 뱃지가 없다
        PartyDetailResult detail = new PartyDetailResult(PARTY_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 3, 2, 500, 500, "ACTIVE", CREATED_AT,
                List.of(PartyDetailResult.MemberInfo.of(new MemberSummary(PUBLIC_ID, "방장", "host.png"), 4, true, false),
                        PartyDetailResult.MemberInfo.of(null, 0, false, false)),
                12000, 25, "encoded-path", null, 7L, "REALTIME", null, false);

        doReturn(detail).when(partyService).getPartyDetail(eq(PARTY_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0]", hasKey("badgeId")))
                .andExpect(jsonPath("$.members[0].badgeId").value(nullValue()))
                .andExpect(jsonPath("$.members[1]", hasKey("badgeId")))
                .andExpect(jsonPath("$.members[1].badgeId").value(nullValue()));
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId} - 토큰이 없으면 401")
    @Test
    void detail_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms/{partyId}", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId} - partyId 가 숫자가 아니면 400")
    @Test
    void detail_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms/{partyId}", "abc").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId} - 없는 방이면 404")
    @Test
    void detail_partyNotFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND)).when(partyService).getPartyDetail(eq(PARTY_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARTY_NOT_FOUND"));
    }

    // ───────────────────────── GET /matching/rooms ─────────────────────────

    @DisplayName("GET /api/v1/matching/rooms - 파라미터가 없으면 ACTIVE 전체를 돌려주고 영역 조회를 하지 않는다")
    @Test
    void list() throws Exception {

        /*
         * settings
         */
        doReturn(List.of(partyResult())).when(partyService).findActiveParties();

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list", hasSize(1)))
                .andExpect(jsonPath("$.list[0].partyId").value(PARTY_ID))
                .andExpect(jsonPath("$.list[0].departure").value("강남역"))
                .andExpect(jsonPath("$.list[0].destination").value("판교역"))
                .andExpect(jsonPath("$.list[0].currentMembers").value(1))
                .andExpect(jsonPath("$.list[0].capacity").value(3))
                .andExpect(jsonPath("$.list[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.list[0].departureLat").value(37.4979))
                .andExpect(jsonPath("$.list[0].departureLng").value(127.0276));
        verify(partyService, never()).findActivePartiesWithin(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @DisplayName("GET /api/v1/matching/rooms - 방이 없으면 빈 목록")
    @Test
    void list_empty() throws Exception {

        /*
         * settings
         */
        doReturn(List.of()).when(partyService).findActiveParties();

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list", hasSize(0)));
    }

    @DisplayName("GET /api/v1/matching/rooms - 토큰이 없으면 401")
    @Test
    void list_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    // ───────────────────────── GET /matching/rooms?swLat... ─────────────────────────

    @DisplayName("GET /api/v1/matching/rooms?swLat... - 네 좌표가 모두 있으면 영역 조회를 하고 전체 조회를 하지 않는다")
    @Test
    void listWithin() throws Exception {

        /*
         * settings
         */
        List<PartySummary> summaries = List.of(new PartySummary(PARTY_ID, "강남역", "판교역", 2L, 3, PartyStatus.ACTIVE, 37.4979, 127.0276));

        doReturn(summaries).when(partyService).findActivePartiesWithin(eq(37.0), eq(127.0), eq(38.0), eq(128.0));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms").with(loginUser())
                        .param("swLat", "37.0").param("swLng", "127.0").param("neLat", "38.0").param("neLng", "128.0"))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list", hasSize(1)))
                .andExpect(jsonPath("$.list[0].partyId").value(PARTY_ID))
                .andExpect(jsonPath("$.list[0].departure").value("강남역"))
                .andExpect(jsonPath("$.list[0].destination").value("판교역"))
                .andExpect(jsonPath("$.list[0].currentMembers").value(2))
                .andExpect(jsonPath("$.list[0].capacity").value(3))
                .andExpect(jsonPath("$.list[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.list[0].departureLat").value(37.4979))
                .andExpect(jsonPath("$.list[0].departureLng").value(127.0276));
        verify(partyService, never()).findActiveParties();
    }

    @DisplayName("GET /api/v1/matching/rooms?swLat... - 영역 안에 방이 없으면 빈 목록")
    @Test
    void listWithin_empty() throws Exception {

        /*
         * settings
         */
        doReturn(List.of()).when(partyService).findActivePartiesWithin(eq(37.0), eq(127.0), eq(38.0), eq(128.0));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms").with(loginUser())
                        .param("swLat", "37.0").param("swLng", "127.0").param("neLat", "38.0").param("neLng", "128.0"))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list", hasSize(0)));
    }

    @DisplayName("GET /api/v1/matching/rooms?swLat... - 토큰이 없으면 401")
    @Test
    void listWithin_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms")
                        .param("swLat", "37.0").param("swLng", "127.0").param("neLat", "38.0").param("neLng", "128.0"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    @DisplayName("GET /api/v1/matching/rooms?swLat... - 좌표가 숫자가 아니면 400")
    @Test
    void listWithin_notNumber() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms").with(loginUser())
                        .param("swLat", "abc").param("swLng", "127.0").param("neLat", "38.0").param("neLng", "128.0"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("GET /api/v1/matching/rooms?swLat... - 좌표가 빈 값이면 400")
    @Test
    void listWithin_blankParam() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms").with(loginUser())
                        .param("swLat", "").param("swLng", "127.0").param("neLat", "38.0").param("neLng", "128.0"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest());
        verifyNoInteractions(partyService);
    }

    @DisplayName("GET /api/v1/matching/rooms?swLat... - 영역 좌표가 뒤집혔으면 400 INVALID_MAP_BOUNDS")
    @Test
    void listWithin_invalidBounds() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.INVALID_MAP_BOUNDS)).when(partyService).findActivePartiesWithin(eq(38.0), eq(127.0), eq(37.0), eq(128.0));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms").with(loginUser())
                        .param("swLat", "38.0").param("swLng", "127.0").param("neLat", "37.0").param("neLng", "128.0"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MAP_BOUNDS"));
    }

    // ───────────────────────── POST /matching/rooms/{partyId}/confirm ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/confirm - 토큰의 사용자와 약속 시간으로 확정하고 본문 없이 204")
    @Test
    void confirm() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/confirm", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody("\"2026-11-03T08:30:00Z\"")))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(partyService).confirm(eq(PARTY_ID), eq(USER_ID), eq(CONFIRMED_AT));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/confirm - 시간대가 붙은 ISO-8601 도 같은 순간으로 읽는다")
    @Test
    void confirm_offsetTime() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/confirm", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody("\"2026-11-03T17:30:00+09:00\"")))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(partyService).confirm(eq(PARTY_ID), eq(USER_ID), eq(CONFIRMED_AT));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/confirm - 토큰이 없으면 401")
    @Test
    void confirm_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/confirm", PARTY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody("\"2026-11-03T08:30:00Z\"")))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/confirm - 약속 시간이 없거나 null 이면 400 이고 서비스를 부르지 않는다")
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "빈 객체 | {}",
            "null | {\"departureAt\": null}"
    })
    void confirm_missingDepartureAt(String name, String body) throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/confirm", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/confirm - 본문이 없으면 400 이고 서비스를 부르지 않는다")
    @Test
    void confirm_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/confirm", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/confirm - 약속 시간 형식이 틀리면 400 이고 서비스를 부르지 않는다")
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "날짜 아님 | \"내일 저녁\"",
            "시간대 없음 | \"2026-11-03 08:30\""
    })
    void confirm_invalidDepartureAt(String name, String value) throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/confirm", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody(value)))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/confirm - partyId 가 숫자가 아니면 400")
    @Test
    void confirm_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/confirm", "abc").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody("\"2026-11-03T08:30:00Z\"")))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/confirm - 서비스의 비즈니스 예외를 상태코드와 오류 코드로 바꾼다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "INVALID_MEETING_TIME, 400",
            "NOT_PARTY_MEMBER, 403",
            "NOT_PARTY_HOST, 403",
            "PARTY_NOT_FOUND, 404",
            "NOT_REALTIME_PARTY, 409",
            "ALREADY_CONFIRMED, 409",
            "PARTY_NOT_RECRUITING, 409",
            "NOT_ENOUGH_MEMBERS, 409"
    })
    void confirm_businessException(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(partyService).confirm(eq(PARTY_ID), eq(USER_ID), eq(CONFIRMED_AT));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/confirm", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(confirmBody("\"2026-11-03T08:30:00Z\"")))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/close - 약속 시간 확정으로 바뀌어 옛 모집 종료 경로는 없다")
    @Test
    void close_removed() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/close", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound());
        verifyNoInteractions(partyService);
    }

    private static String confirmBody(String departureAtJson) {
        return "{\"departureAt\": " + departureAtJson + "}";
    }

    // ───────────────────────── POST /matching/rooms/{partyId}/arrive ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/arrive - 토큰의 사용자와 지금 위치로 도착을 남기고 본문 없이 204")
    @Test
    void arrive() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/arrive", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(arriveBody("37.4983", "127.0276")))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(partyService).arrive(eq(PARTY_ID), eq(USER_ID), eq(37.4983), eq(127.0276));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/arrive - 토큰이 없으면 401")
    @Test
    void arrive_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/arrive", PARTY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(arriveBody("37.4983", "127.0276")))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/arrive - 좌표가 없거나 null 이면 400 이고 서비스를 부르지 않는다")
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "빈 객체 | {}",
            "위도 누락 | {\"longitude\": 127.0276}",
            "경도 누락 | {\"latitude\": 37.4983}",
            "위도 null | {\"latitude\": null, \"longitude\": 127.0276}",
            "경도 null | {\"latitude\": 37.4983, \"longitude\": null}"
    })
    void arrive_missingCoordinate(String name, String body) throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/arrive", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/arrive - 본문이 없으면 400 이고 서비스를 부르지 않는다")
    @Test
    void arrive_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/arrive", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/arrive - 좌표가 숫자가 아니면 400 이고 서비스를 부르지 않는다")
    @Test
    void arrive_invalidCoordinate() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/arrive", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(arriveBody("\"강남역\"", "127.0276")))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/arrive - partyId 가 숫자가 아니면 400")
    @Test
    void arrive_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/arrive", "abc").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(arriveBody("37.4983", "127.0276")))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/arrive - 서비스의 비즈니스 예외를 상태코드와 오류 코드로 바꾼다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "OUT_OF_SERVICE_AREA, 400",
            "TOO_FAR_FROM_DEPARTURE, 400",
            "NOT_PARTY_MEMBER, 403",
            "PARTY_NOT_FOUND, 404",
            "PARTY_CLOSED, 409",
            "PARTY_NOT_CONFIRMED, 409",
            "MEETING_NOT_STARTED, 409"
    })
    void arrive_businessException(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(partyService).arrive(eq(PARTY_ID), eq(USER_ID), eq(37.4983), eq(127.0276));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/arrive", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(arriveBody("37.4983", "127.0276")))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    private static String arriveBody(String latitudeJson, String longitudeJson) {
        return "{\"latitude\": " + latitudeJson + ", \"longitude\": " + longitudeJson + "}";
    }

    // ───────────────────────── POST /matching/rooms/{partyId}/depart ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/depart - 토큰의 사용자로 출발하고 본문 없이 204")
    @Test
    void depart() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/depart", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(partyService).depart(eq(PARTY_ID), eq(USER_ID));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/depart - 토큰이 없으면 401")
    @Test
    void depart_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/depart", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/depart - partyId 가 숫자가 아니면 400")
    @Test
    void depart_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/depart", "abc").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/depart - 서비스의 비즈니스 예외를 상태코드와 오류 코드로 바꾼다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "NOT_PARTY_MEMBER, 403",
            "PARTY_NOT_FOUND, 404",
            "PARTY_CLOSED, 409",
            "PARTY_NOT_CONFIRMED, 409",
            "NOT_ENOUGH_MEMBERS, 409",
            "MEETING_NOT_STARTED, 409",
            "NOT_ARRIVED, 409"
    })
    void depart_businessException(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(partyService).depart(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/depart", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    // ───────────────────────── POST /matching/rooms/{partyId}/finish ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/finish - 토큰의 사용자로 종료하고 본문 없이 204")
    @Test
    void finish() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/finish", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(partyService).finish(eq(PARTY_ID), eq(USER_ID));
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/finish - 토큰이 없으면 401")
    @Test
    void finish_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/finish", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/{partyId}/finish - 서비스의 비즈니스 예외를 상태코드와 오류 코드로 바꾼다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "NOT_PARTY_MEMBER, 403",
            "NOT_PARTY_HOST, 403",
            "PARTY_NOT_FOUND, 404",
            "PARTY_NOT_COMPLETED, 409",
            "USE_DEPART_FOR_CONFIRMED_PARTY, 409"
    })
    void finish_businessException(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(partyService).finish(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/{partyId}/finish", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    // ───────────────────────── POST /matching/rooms/realtime ─────────────────────────

    @DisplayName("POST /api/v1/matching/rooms/realtime - 토큰의 사용자와 좌표로 후보를 조회해 돌려준다")
    @Test
    void getListCandidate() throws Exception {

        /*
         * settings
         */
        NearbyPartySummary summary = new NearbyPartySummary(PARTY_ID, 37.50, 127.03, 37.40, 127.12, "강남역", "판교역", 3, 1);

        doReturn(List.of(new MatchCandidate(summary, 150))).when(partyService).realtimeMatch(eq(USER_ID), eq(REALTIME_COMMAND));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/realtime").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(REALTIME_BODY))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].totalSeconds").value(150))
                .andExpect(jsonPath("$[0].summary.id").value(PARTY_ID))
                .andExpect(jsonPath("$[0].summary.departureLat").value(37.50))
                .andExpect(jsonPath("$[0].summary.departureLng").value(127.03))
                .andExpect(jsonPath("$[0].summary.destinationLat").value(37.40))
                .andExpect(jsonPath("$[0].summary.destinationLng").value(127.12))
                .andExpect(jsonPath("$[0].summary.departure").value("강남역"))
                .andExpect(jsonPath("$[0].summary.destination").value("판교역"))
                .andExpect(jsonPath("$[0].summary.capacity").value(3))
                .andExpect(jsonPath("$[0].summary.currentMembers").value(1));
        verify(partyService).realtimeMatch(eq(USER_ID), eq(REALTIME_COMMAND));
    }

    @DisplayName("POST /api/v1/matching/rooms/realtime - 후보가 없으면 빈 배열")
    @Test
    void getListCandidate_empty() throws Exception {

        /*
         * settings
         */
        doReturn(List.of()).when(partyService).realtimeMatch(eq(USER_ID), eq(REALTIME_COMMAND));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/realtime").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(REALTIME_BODY))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @DisplayName("POST /api/v1/matching/rooms/realtime - 토큰이 없으면 401")
    @Test
    void getListCandidate_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/realtime").contentType(MediaType.APPLICATION_JSON).content(REALTIME_BODY))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/realtime - 본문이 없으면 400")
    @Test
    void getListCandidate_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/realtime").with(loginUser()).contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/realtime - 좌표가 누락되면 400 이고 서비스를 부르지 않는다")
    @Test
    void getListCandidate_missingCoordinate() throws Exception {

        /*
         * settings
         */
        final String body = """
                {
                  "departureLng": 127.0276,
                  "destLat": 37.3948,
                  "destLng": 127.1112
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/realtime").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest());
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/realtime - 좌표가 null 이면 400 이고 서비스를 부르지 않는다")
    @Test
    void getListCandidate_nullCoordinate() throws Exception {

        /*
         * settings
         */
        final String body = """
                {
                  "departureLat": null,
                  "departureLng": 127.0276,
                  "destLat": 37.3948,
                  "destLng": 127.1112
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/realtime").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest());
        verifyNoInteractions(partyService);
    }

    @DisplayName("POST /api/v1/matching/rooms/realtime - 서비스의 비즈니스 예외를 상태코드와 오류 코드로 바꾼다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "OUT_OF_SERVICE_AREA, 400",
            "ALREADY_JOINED_OTHER_PARTY, 409",
            "ROUTE_SEARCH_FAILED, 502"
    })
    void getListCandidate_businessException(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(partyService).realtimeMatch(eq(USER_ID), any(RealTimeMatchingCommand.class));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/rooms/realtime").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(REALTIME_BODY))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }

    private static PartyResult partyResult() {
        return new PartyResult(PARTY_ID, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 3, 1, 500, 500, "ACTIVE", CREATED_AT, 12000, 25, "encoded-path", "REALTIME", null);
    }
}
