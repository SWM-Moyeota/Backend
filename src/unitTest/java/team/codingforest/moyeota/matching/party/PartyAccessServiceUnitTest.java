package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.api.dto.PartyChatSummary;
import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.matching.api.dto.PartyRiders;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PartyAccessServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long STRANGER_ID = 3L;
    private static final Long NO_SHOW_ID = 4L;
    private static final Long VOIDED_ID = 5L;
    private static final Instant BASE_TIME = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant MEETING_AT = BASE_TIME.plus(Duration.ofHours(1));

    @Mock
    private Parties parties;

    @InjectMocks
    private PartyAccessService partyAccessService;

    // ───────────────────────── findChatSummary ─────────────────────────

    @DisplayName("findChatSummary - 합류 순 멤버와 장소를 돌려준다")
    @Test
    void findChatSummary() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.COMPLETED, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        PartyChatSummary result = partyAccessService.findChatSummary(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).isEqualTo(new PartyChatSummary(PARTY_ID, List.of(HOST_ID, MEMBER_ID), "강남역", "판교역"));
    }

    @DisplayName("findChatSummary - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void findChatSummary_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> partyAccessService.findChatSummary(PARTY_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_FOUND);
    }

    // ───────────────────────── isOnboardingMember ─────────────────────────

    @DisplayName("isOnboardingMember - 멤버이고 진행 중인 상태면 true")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"}, mode = EnumSource.Mode.EXCLUDE)
    void isOnboardingMember(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isTrue();
    }

    @DisplayName("isOnboardingMember - 멤버여도 닫힌 방이면 false")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void isOnboardingMember_closed(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("isOnboardingMember - 진행 중이어도 멤버가 아니면 false")
    @Test
    void isOnboardingMember_notMember() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.ACTIVE, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, STRANGER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    @DisplayName("isOnboardingMember - 없는 방이면 예외 없이 false")
    @Test
    void isOnboardingMember_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        boolean result = partyAccessService.isOnboardingMember(PARTY_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(result).isFalse();
    }

    // ───────────────────────── findRiders ─────────────────────────

    @DisplayName("findRiders - 끝난 방이면 finished 이고, 멤버와 탑승자(도착했고 노쇼가 아닌 멤버)를 합류 순으로 준다. 노쇼 멤버는 탑승자가 아니다")
    @Test
    void findRiders() {

        /*
         * settings
         */
        Party party = attendedParty(PartyStatus.FINISHED, List.of(
                arrived(MEMBER_ID, 0),
                PartyMember.load(NO_SHOW_ID, BASE_TIME.plusSeconds(1), null, MEETING_AT, null),
                arrived(HOST_ID, 2)));
        doReturn(Optional.of(party)).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Optional<PartyRiders> result = partyAccessService.findRiders(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).contains(new PartyRiders(PARTY_ID, true, List.of(MEMBER_ID, NO_SHOW_ID, HOST_ID), List.of(MEMBER_ID, HOST_ID)));
    }

    @DisplayName("findRiders - 노쇼가 무효로 돌아갔어도 도착을 누르지 않은 멤버는 탑승자가 아니다")
    @Test
    void findRiders_voidedNoShow() {

        /*
         * settings
         */
        Party party = attendedParty(PartyStatus.FINISHED, List.of(
                arrived(HOST_ID, 0),
                PartyMember.load(VOIDED_ID, BASE_TIME.plusSeconds(1), null, MEETING_AT, MEETING_AT.plus(Duration.ofDays(1))),
                arrived(MEMBER_ID, 2)));
        doReturn(Optional.of(party)).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        PartyRiders result = partyAccessService.findRiders(PARTY_ID).orElseThrow();

        /*
         * verify
         */
        assertThat(result.memberIds()).containsExactly(HOST_ID, VOIDED_ID, MEMBER_ID);
        assertThat(result.riderIds()).containsExactly(HOST_ID, MEMBER_ID);
    }

    @DisplayName("findRiders - 동승이 끝나지 않은(FINISHED 가 아닌) 방은 finished 가 false 다")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED"}, mode = EnumSource.Mode.EXCLUDE)
    void findRiders_notFinished(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(attendedParty(status, List.of(arrived(HOST_ID, 0), arrived(MEMBER_ID, 1))))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        PartyRiders result = partyAccessService.findRiders(PARTY_ID).orElseThrow();

        /*
         * verify
         */
        assertThat(result.finished()).isFalse();
        assertThat(result.memberIds()).containsExactly(HOST_ID, MEMBER_ID);
    }

    @DisplayName("findRiders - 도착 없이 끝난 방(약속을 정하지 않은 구버전 종료)은 탑승자가 없다")
    @Test
    void findRiders_noArrivals() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.FINISHED, HOST_ID, MEMBER_ID))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        PartyRiders result = partyAccessService.findRiders(PARTY_ID).orElseThrow();

        /*
         * verify
         */
        assertThat(result.finished()).isTrue();
        assertThat(result.memberIds()).containsExactly(HOST_ID, MEMBER_ID);
        assertThat(result.riderIds()).isEmpty();
    }

    @DisplayName("findRiders - 없는 방이면 예외 없이 empty")
    @Test
    void findRiders_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Optional<PartyRiders> result = partyAccessService.findRiders(PARTY_ID);

        /*
         * verify
         */
        assertThat(result).isEmpty();
    }

    // ───────────────────────── findPlaces ─────────────────────────

    @DisplayName("findPlaces - 방 id 별 출발지·도착지 이름을 한 번에 읽어 그대로 준다")
    @Test
    void findPlaces() {

        /*
         * settings
         */
        Map<Long, PartyPlaces> places = Map.of(PARTY_ID, new PartyPlaces("강남역", "판교역"), 200L, new PartyPlaces("서울역", "홍대입구역"));
        doReturn(places).when(parties).findPlaces(eq(List.of(PARTY_ID, 200L, 300L)));

        /*
         * test
         */
        Map<Long, PartyPlaces> result = partyAccessService.findPlaces(List.of(PARTY_ID, 200L, 300L));

        /*
         * verify
         */
        assertThat(result).isEqualTo(places);
        verify(parties).findPlaces(eq(List.of(PARTY_ID, 200L, 300L)));
    }

    // ───────────────────────── 공통 도우미 ─────────────────────────

    /** memberIds 앞쪽일수록 먼저 합류한 멤버다. 정원은 3 으로 고정 */
    private static Party party(PartyStatus status, Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for (int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], BASE_TIME.plusSeconds(i)));
        }

        return Party.load(PARTY_ID, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), "강남역", "판교역",
                new Capacity(3), members, BASE_TIME, status, 12000, 25, "encoded-path", 0L);
    }

    /** members 순서대로 합류한 약속이 정해진 방 */
    private static Party attendedParty(PartyStatus status, List<PartyMember> members) {
        return Party.load(PARTY_ID, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), "강남역", "판교역",
                new Capacity(3), new ArrayList<>(members), members.getFirst().getMemberId(), BASE_TIME, status, 12000, 25, "encoded-path",
                0L, RoomType.REALTIME, MEETING_AT, null);
    }

    private static PartyMember arrived(Long memberId, int joinOrder) {
        return PartyMember.load(memberId, BASE_TIME.plusSeconds(joinOrder), MEETING_AT.minusSeconds(60), null, null);
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
