package team.codingforest.moyeota.matching.party.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  방 상세(GET /matching/rooms/{partyId})와 SSE connected 가 함께 쓰는 응답 모양.
 *  기사 기능을 없앴지만 출시된 앱이 taxiDriverId 를 읽으므로, 방 도메인에 기사 번호가 없어도 키는 null 로 남아야 한다.
 *  반경도 방 도메인에 없지만 출시된 앱이 departureRadius·destinationRadius 를 null 이 될 수 없는 정수로 읽으므로 키는 500 으로 남아야 한다
 */
class PartyDetailResultTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @DisplayName("from - 정원이 찬 방이어도 taxiDriverId 는 null 이고 상태는 ACTIVE 다")
    @Test
    void from_taxiDriverIdIsNull() {

        /*
         * settings
         */
        Party party = fullParty();

        /*
         * test
         */
        PartyDetailResult result = PartyDetailResult.from(party, Map.of(), Map.of());

        /*
         * verify
         */
        assertThat(result.taxiDriverId()).isNull();
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @DisplayName("from - JSON 으로 내보내면 taxiDriverId 키는 남고 값은 null 이다")
    @Test
    void from_taxiDriverIdKeyKeptAsNull() {

        /*
         * settings
         */
        PartyDetailResult result = PartyDetailResult.from(fullParty(), Map.of(), Map.of());

        /*
         * test
         */
        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(result));

        /*
         * verify
         */
        assertThat(json.has("taxiDriverId")).as("키가 빠지면 출시된 앱의 응답 해석이 달라진다").isTrue();
        assertThat(json.get("taxiDriverId").isNull()).isTrue();
    }

    @DisplayName("from - 실시간 방을 JSON 으로 내보내면 departureRadius·destinationRadius 키가 500 으로 남는다")
    @Test
    void from_radiusKeysKeptAsFixedValue() {

        /*
         * settings
         */
        PartyDetailResult result = PartyDetailResult.from(fullParty(), Map.of(), Map.of());

        /*
         * test
         */
        JsonNode json = jsonMapper.readTree(jsonMapper.writeValueAsString(result));

        /*
         * verify
         */
        assertThat(json.has("departureRadius")).as("키가 빠지면 출시된 앱의 응답 해석이 깨진다").isTrue();
        assertThat(json.has("destinationRadius")).as("키가 빠지면 출시된 앱의 응답 해석이 깨진다").isTrue();
        assertThat(json.get("departureRadius").isInt()).isTrue();
        assertThat(json.get("departureRadius").asInt()).isEqualTo(500);
        assertThat(json.get("destinationRadius").isInt()).isTrue();
        assertThat(json.get("destinationRadius").asInt()).isEqualTo(500);
    }

    @DisplayName("from - 예약 방도 departureRadius·destinationRadius 는 500 이다")
    @Test
    void from_reservationRadiusIsFixedValue() {

        /*
         * settings
         */
        Party reservation = Party.load(200L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), "강남역", "판교역",
                new Capacity(3), List.of(PartyMember.load(1L, CREATED_AT, null)), 1L, CREATED_AT, PartyStatus.ACTIVE, null, null, null, 0L,
                RoomType.RESERVED, CREATED_AT.plus(Duration.ofDays(1)), null, null, 0);

        /*
         * test
         */
        PartyDetailResult result = PartyDetailResult.from(reservation, Map.of(), Map.of());

        /*
         * verify
         */
        assertThat(result.departureRadius()).isEqualTo(500);
        assertThat(result.destinationRadius()).isEqualTo(500);
        assertThat(result.roomType()).isEqualTo("RESERVED");
    }

    /** 정원이 찬 실시간 방 - 정원이 차도 ACTIVE 다. 예전에는 정원이 차면 COMPLETED 가 되고 기사가 배정됐다 */
    private static Party fullParty() {
        List<PartyMember> members = List.of(PartyMember.load(1L, CREATED_AT, null), PartyMember.load(2L, CREATED_AT.plusSeconds(1), null));

        return Party.load(100L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), "강남역", "판교역",
                new Capacity(2), members, 1L, CREATED_AT, PartyStatus.ACTIVE, 12000, 25, "encoded-path", 0L,
                RoomType.REALTIME, null, null, null, 0);
    }
}
