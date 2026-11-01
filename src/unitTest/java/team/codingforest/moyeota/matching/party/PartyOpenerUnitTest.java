package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.api.dto.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.completion.PartyCompletionPolicy;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.party.dto.OpenPartyCommand;
import team.codingforest.moyeota.matching.party.dto.PartyResult;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PartyOpenerUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long HOST_ID = 1L;
    private static final double DEPARTURE_LAT = 37.4979;
    private static final double DEPARTURE_LNG = 127.0276;
    private static final double DESTINATION_LAT = 37.3948;
    private static final double DESTINATION_LNG = 127.1112;
    private static final Location DEPARTURE = new Location(DEPARTURE_LAT, DEPARTURE_LNG);
    private static final Location DESTINATION = new Location(DESTINATION_LAT, DESTINATION_LNG);
    private static final Instant BASE_TIME = Instant.parse("2026-10-01T00:00:00Z");
    private static final RouteEstimate ESTIMATE = new RouteEstimate(12000, 25, "encoded-path");

    @Mock
    private Parties parties;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PartyCompletionPolicy partyCompletionPolicy;

    @InjectMocks
    private PartyOpener partyOpener;

    @DisplayName("open - 정원이 남으면 생성자가 멤버인 ACTIVE 방을 저장하고 참여 이벤트를 발행한다")
    @Test
    void open() {

        /*
         * settings
         */
        Party saved = party(PartyStatus.ACTIVE, 3);

        doReturn(false).when(parties).existsOngoingByMemberId(eq(HOST_ID));
        doReturn(saved).when(parties).save(any(Party.class));

        /*
         * test
         */
        PartyResult result = partyOpener.open(openCommand(3), ESTIMATE);

        /*
         * verify
         */
        ArgumentCaptor<Party> captor = ArgumentCaptor.forClass(Party.class);
        verify(parties).save(captor.capture());
        Party opened = captor.getValue();
        assertThat(opened.getId()).isNull();
        assertThat(opened.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(opened.hasMember(HOST_ID)).isTrue();
        assertThat(opened.getMembers()).hasSize(1);
        assertThat(opened.getDepartureLocation()).isEqualTo(DEPARTURE);
        assertThat(opened.getDestinationLocation()).isEqualTo(DESTINATION);
        assertThat(opened.getDeparture()).isEqualTo("강남역");
        assertThat(opened.getDestination()).isEqualTo("판교역");
        assertThat(opened.getCapacity().value()).isEqualTo(3);
        assertThat(opened.getDepartureRadius()).isEqualTo(Radius.DEFAULT);
        assertThat(opened.getDestinationRadius()).isEqualTo(Radius.DEFAULT);
        assertThat(opened.getEstimatedFare()).isEqualTo(12000);
        assertThat(opened.getEstimatedTime()).isEqualTo(25);
        assertThat(opened.getRoute()).isEqualTo("encoded-path");

        assertThat(result.id()).isEqualTo(PARTY_ID);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.currentMembers()).isEqualTo(1);

        verify(eventPublisher).publishEvent(eq(new PartyMemberJoinedEvent(PARTY_ID, HOST_ID)));
        verifyNoInteractions(partyCompletionPolicy);
    }

    @DisplayName("open - 혼자 타는 방은 완료 정책을 돌려 다시 저장하고 참여 이벤트는 내지 않는다")
    @Test
    void open_fullOnCreate() {

        /*
         * settings
         */
        Party saved = party(PartyStatus.COMPLETED, 1);

        doReturn(false).when(parties).existsOngoingByMemberId(eq(HOST_ID));
        doReturn(saved).when(parties).save(any(Party.class));
        doAnswer(invocation -> {
            saved.startMatching(BASE_TIME);
            return null;
        }).when(partyCompletionPolicy).onCompleted(eq(saved));

        /*
         * test
         */
        PartyResult result = partyOpener.open(openCommand(1), ESTIMATE);

        /*
         * verify
         */
        assertThat(result.status()).as("완료 정책이 바꾼 상태가 응답에 실린다").isEqualTo("MATCHING");
        verify(parties, times(2)).save(any(Party.class));
        verify(parties).save(eq(saved));
        verify(partyCompletionPolicy).onCompleted(eq(saved));
        verifyNoInteractions(eventPublisher);
    }

    /** 경로 조회는 트랜잭션 밖이라 PartyService 의 첫 검사와 저장 사이에 다른 방에 들어갈 수 있다 */
    @DisplayName("open - 경로 조회 중 다른 방에 들어갔으면 ALREADY_JOINED_OTHER_PARTY 이고 저장하지 않는다")
    @Test
    void open_joinedOtherPartyDuringRouteSearch() {

        /*
         * settings
         */
        doReturn(true).when(parties).existsOngoingByMemberId(eq(HOST_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyOpener.open(openCommand(3), ESTIMATE));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_JOINED_OTHER_PARTY);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher, partyCompletionPolicy);
    }

    @DisplayName("open - 출발지와 도착지가 같으면 SAME_DEPARTURE_DESTINATION")
    @Test
    void open_sameLocation() {

        /*
         * settings
         */
        OpenPartyCommand command = new OpenPartyCommand(HOST_ID, DEPARTURE_LAT, DEPARTURE_LNG, DEPARTURE_LAT, DEPARTURE_LNG, "강남역", "강남역", 3);

        doReturn(false).when(parties).existsOngoingByMemberId(eq(HOST_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyOpener.open(command, ESTIMATE));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.SAME_DEPARTURE_DESTINATION);
        verify(parties, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @DisplayName("open - 출발지나 도착지가 서비스 지역 밖이거나 NaN 이면 OUT_OF_SERVICE_AREA")
    @ParameterizedTest(name = "{0}, {1} → {2}, {3}")
    @CsvSource({
            "32.99, 127.0, 37.0, 127.0",
            "39.01, 127.0, 37.0, 127.0",
            "37.0, 123.99, 37.0, 127.0",
            "37.0, 132.01, 37.0, 127.0",
            "37.0, 127.0, 32.99, 127.0",
            "37.0, 127.0, 39.01, 127.0",
            "37.0, 127.0, 37.0, 123.99",
            "37.0, 127.0, 37.0, 132.01",
            "NaN, 127.0, 37.0, 127.0",
            "37.0, NaN, 37.0, 127.0",
            "37.0, 127.0, NaN, 127.0",
            "37.0, 127.0, 37.0, NaN"
    })
    void open_outOfServiceArea(double departureLat, double departureLng, double destinationLat, double destinationLng) {

        /*
         * settings
         */
        OpenPartyCommand command = new OpenPartyCommand(HOST_ID, departureLat, departureLng, destinationLat, destinationLng, "출발", "도착", 3);

        doReturn(false).when(parties).existsOngoingByMemberId(eq(HOST_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyOpener.open(command, ESTIMATE));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.OUT_OF_SERVICE_AREA);
        verify(parties, never()).save(any());
    }

    @DisplayName("open - 정원이 1~3 밖이면 INVALID_CAPACITY")
    @ParameterizedTest(name = "capacity={0}")
    @ValueSource(ints = {-1, 0, 4})
    void open_invalidCapacity(int capacity) {

        /*
         * settings
         */
        doReturn(false).when(parties).existsOngoingByMemberId(eq(HOST_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyOpener.open(openCommand(capacity), ESTIMATE));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INVALID_CAPACITY);
        verify(parties, never()).save(any());
    }

    @DisplayName("open - 경로 산출값이 null 이거나 음수면 INVALID_ROUTE_ESTIMATE")
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEstimates")
    void open_invalidRouteEstimate(RouteEstimate estimate) {

        /*
         * settings
         */
        doReturn(false).when(parties).existsOngoingByMemberId(eq(HOST_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyOpener.open(openCommand(3), estimate));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.INVALID_ROUTE_ESTIMATE);
        verify(parties, never()).save(any());
    }

    static Stream<RouteEstimate> invalidEstimates() {
        return Stream.of(
                new RouteEstimate(null, 25, "path"),
                new RouteEstimate(-1, 25, "path"),
                new RouteEstimate(12000, null, "path"),
                new RouteEstimate(12000, -1, "path"),
                new RouteEstimate(12000, 25, null));
    }

    @DisplayName("open - 요금과 시간이 경계값 0 이면 생성된다")
    @Test
    void open_zeroEstimate() {

        /*
         * settings
         */
        doReturn(false).when(parties).existsOngoingByMemberId(eq(HOST_ID));
        doReturn(party(PartyStatus.ACTIVE, 3)).when(parties).save(any(Party.class));

        /*
         * test
         */
        partyOpener.open(openCommand(3), new RouteEstimate(0, 0, ""));

        /*
         * verify
         */
        ArgumentCaptor<Party> captor = ArgumentCaptor.forClass(Party.class);
        verify(parties).save(captor.capture());
        assertThat(captor.getValue().getEstimatedFare()).isZero();
        assertThat(captor.getValue().getEstimatedTime()).isZero();
        assertThat(captor.getValue().getRoute()).isEmpty();
    }

    @DisplayName("open - 방 저장은 트랜잭션 안에서 돈다")
    @Test
    void open_transactional() throws Exception {

        /*
         * test
         */
        boolean transactional = PartyOpener.class.getMethod("open", OpenPartyCommand.class, RouteEstimate.class)
                .isAnnotationPresent(Transactional.class);

        /*
         * verify
         */
        assertThat(transactional).isTrue();
    }

    private static OpenPartyCommand openCommand(int capacity) {
        return new OpenPartyCommand(HOST_ID, DEPARTURE_LAT, DEPARTURE_LNG, DESTINATION_LAT, DESTINATION_LNG, "강남역", "판교역", capacity);
    }

    private static Party party(PartyStatus status, int capacity) {
        return Party.load(PARTY_ID, DEPARTURE, DESTINATION, Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역", new Capacity(capacity),
                List.of(PartyMember.load(HOST_ID, BASE_TIME)),
                BASE_TIME, status, 12000, 25, "encoded-path", null, null, null, 0L);
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
