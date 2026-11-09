package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  정리 스케줄러의 자동 정리(autoClose) - 약속이 정해진 방은 출발과 같은 기준으로 노쇼를 가르고, 약속을 정하지 않은 방은 노쇼 없이 닫는다
 */
class PartyAutoCloseUnitTest {
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long THIRD_ID = 3L;
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    // 약속(출발) 시각에서 1일이 지났다
    private static final Instant MEETING_AT = NOW.minus(Duration.ofDays(1));
    private static final Instant ARRIVED_AT = MEETING_AT.plusSeconds(30);

    // ───────────────────────── 약속이 정해진 방 ─────────────────────────

    @DisplayName("autoClose - 도착한 멤버가 있는 확정 방은 FINISHED 로 끝나고 도착하지 않은 멤버만 노쇼로 찍혀 돌아온다")
    @Test
    void autoClose_someArrived() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, arrivedMember(HOST_ID), member(MEMBER_ID), arrivedMember(THIRD_ID));

        /*
         * test
         */
        AutoCloseResult result = party.autoClose(NOW);

        /*
         * verify
         */
        assertThat(result.status()).isEqualTo(PartyStatus.FINISHED);
        assertThat(result.arrivedCount()).isEqualTo(2);
        assertThat(result.noShowMemberIds()).containsExactly(MEMBER_ID);
        assertThat(result.hasRiders()).isTrue();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(1L);
        assertThat(memberOf(party, MEMBER_ID).isNoShow()).isTrue();
        assertThat(memberOf(party, MEMBER_ID).getNoShowAt()).isEqualTo(NOW);
        assertThat(memberOf(party, HOST_ID).isNoShow()).isFalse();
        assertThat(memberOf(party, HOST_ID).getArrivedAt()).as("도착 시각은 그대로").isEqualTo(ARRIVED_AT);
        assertThat(memberOf(party, THIRD_ID).isNoShow()).isFalse();
    }

    @DisplayName("autoClose - 모두 도착했으면 노쇼 없이 FINISHED 다")
    @Test
    void autoClose_everyoneArrived() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, arrivedMember(HOST_ID), arrivedMember(MEMBER_ID));

        /*
         * test
         */
        AutoCloseResult result = party.autoClose(NOW);

        /*
         * verify
         */
        assertThat(result.status()).isEqualTo(PartyStatus.FINISHED);
        assertThat(result.arrivedCount()).isEqualTo(2);
        assertThat(result.noShowMemberIds()).isEmpty();
        assertThat(result.hasRiders()).isTrue();
        assertThat(party.getMembers()).noneMatch(m -> m.getNoShowAt() != null);
        assertThat(party.getVersion()).isEqualTo(1L);
    }

    @DisplayName("autoClose - 아무도 도착하지 않은 확정 방은 동승이 없었으니 CANCELED 이고 멤버 전원이 합류 순으로 노쇼다")
    @Test
    void autoClose_nobodyArrived() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, member(HOST_ID), member(MEMBER_ID), member(THIRD_ID));

        /*
         * test
         */
        AutoCloseResult result = party.autoClose(NOW);

        /*
         * verify
         */
        assertThat(result.status()).isEqualTo(PartyStatus.CANCELED);
        assertThat(result.arrivedCount()).isZero();
        assertThat(result.noShowMemberIds()).containsExactly(HOST_ID, MEMBER_ID, THIRD_ID);
        assertThat(result.hasRiders()).isFalse();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getVersion()).isEqualTo(1L);
        assertThat(party.getMembers()).allMatch(PartyMember::isNoShow);
        assertThat(party.getMembers()).extracting(PartyMember::getNoShowAt).containsOnly(NOW);
    }

    @DisplayName("autoClose - 예약 방도 같은 기준이다. 출발 시각이 지나 아직 ACTIVE 여도 도착한 멤버가 있으면 FINISHED 다")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"})
    void autoClose_reserved(PartyStatus status) {

        /*
         * settings
         */
        Party party = reserved(status, member(HOST_ID), arrivedMember(MEMBER_ID));

        /*
         * test
         */
        AutoCloseResult result = party.autoClose(NOW);

        /*
         * verify
         */
        assertThat(result.status()).isEqualTo(PartyStatus.FINISHED);
        assertThat(result.noShowMemberIds()).containsExactly(HOST_ID);
        assertThat(result.hasRiders()).isTrue();
        assertThat(memberOf(party, HOST_ID).isNoShow()).isTrue();
        assertThat(memberOf(party, MEMBER_ID).isNoShow()).isFalse();
    }

    @DisplayName("autoClose - 아무도 도착하지 않은 예약 방도 CANCELED 이고 전원 노쇼다")
    @Test
    void autoClose_reservedNobodyArrived() {

        /*
         * settings
         */
        Party party = reserved(PartyStatus.COMPLETED, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        AutoCloseResult result = party.autoClose(NOW);

        /*
         * verify
         */
        assertThat(result.status()).isEqualTo(PartyStatus.CANCELED);
        assertThat(result.noShowMemberIds()).containsExactly(HOST_ID, MEMBER_ID);
        assertThat(result.hasRiders()).isFalse();
    }

    @DisplayName("autoClose - 아무도 들어오지 않은 예약 방(방장 혼자)은 모집 정리처럼 노쇼 없이 CANCELED 다 - 도착했어도 혼자서는 탄 게 아니다")
    @ParameterizedTest(name = "도착={0}")
    @ValueSource(booleans = {false, true})
    void autoClose_reservedAlone(boolean arrived) {

        /*
         * settings
         */
        Party party = reserved(PartyStatus.ACTIVE, arrived ? arrivedMember(HOST_ID) : member(HOST_ID));

        /*
         * test
         */
        AutoCloseResult result = party.autoClose(NOW);

        /*
         * verify
         */
        assertThat(result.status()).isEqualTo(PartyStatus.CANCELED);
        assertThat(result.noShowMemberIds()).isEmpty();
        assertThat(result.hasRiders()).isFalse();
        assertThat(memberOf(party, HOST_ID).getNoShowAt()).isNull();
        assertThat(party.getVersion()).isEqualTo(1L);
    }

    @DisplayName("autoClose - 약속 시간 정각이면 닫는다")
    @Test
    void autoClose_atMeetingTime() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        AutoCloseResult result = party.autoClose(MEETING_AT);

        /*
         * verify
         */
        assertThat(result.status()).isEqualTo(PartyStatus.FINISHED);
        assertThat(result.noShowMemberIds()).containsExactly(MEMBER_ID);
    }

    @DisplayName("autoClose - 만든 지 1일이 다 돼 확정해 약속 시간이 아직 오지 않은 방은 MEETING_NOT_STARTED 이고 아무도 노쇼로 찍지 않는다")
    @Test
    void autoClose_beforeMeeting() {

        /*
         * settings
         */
        Party party = realtime(PartyStatus.COMPLETED, NOW.plus(Duration.ofMinutes(40)), member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.autoClose(NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.MEETING_NOT_STARTED, party, PartyStatus.COMPLETED);
    }

    // ───────────────────────── 약속을 정하지 않은 방 ─────────────────────────

    @DisplayName("autoClose - 약속을 정하지 않은 COMPLETED 방은 이전 자동 종료처럼 노쇼 없이 FINISHED 이고 탑승 기록 대상이 아니다")
    @Test
    void autoClose_unconfirmedCompleted() {

        /*
         * settings
         */
        Party party = realtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        AutoCloseResult result = party.autoClose(NOW);

        /*
         * verify
         */
        assertThat(result.status()).isEqualTo(PartyStatus.FINISHED);
        assertThat(result.arrivedCount()).isZero();
        assertThat(result.noShowMemberIds()).isEmpty();
        assertThat(result.hasRiders()).as("누가 탔는지 알 수 없다").isFalse();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(1L);
        assertThat(party.getMembers()).noneMatch(m -> m.getNoShowAt() != null);
    }

    @DisplayName("autoClose - 약속을 정하지 않은 ACTIVE 방은 멤버 수와 관계없이 노쇼 없이 CANCELED 다")
    @Test
    void autoClose_unconfirmedActive() {

        /*
         * settings
         */
        Party alone = realtime(PartyStatus.ACTIVE, null, member(HOST_ID));
        Party two = realtime(PartyStatus.ACTIVE, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        AutoCloseResult aloneResult = alone.autoClose(NOW);
        AutoCloseResult twoResult = two.autoClose(NOW);

        /*
         * verify
         */
        assertThat(aloneResult.status()).isEqualTo(PartyStatus.CANCELED);
        assertThat(twoResult.status()).isEqualTo(PartyStatus.CANCELED);
        assertThat(aloneResult.noShowMemberIds()).isEmpty();
        assertThat(twoResult.noShowMemberIds()).isEmpty();
        assertThat(twoResult.hasRiders()).isFalse();
        assertThat(two.getMembers()).noneMatch(m -> m.getNoShowAt() != null);
        assertThat(two.getVersion()).isEqualTo(1L);
    }

    // ───────────────────────── 거절 ─────────────────────────

    @DisplayName("autoClose - 이미 끝났거나 취소된 방이면 PARTY_CLOSED 이고 상태·version·노쇼가 그대로다")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
    void autoClose_notCreate(PartyStatus status) {

        /*
         * settings
         */
        Party party = confirmedRealtime(status, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.autoClose(NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.PARTY_CLOSED, party, status);
    }

    @DisplayName("autoClose - 이미 자동 정리된 방을 다시 정리하면 PARTY_CLOSED 이고 노쇼 시각이 다시 찍히지 않는다")
    @Test
    void autoClose_twice() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, member(HOST_ID), member(MEMBER_ID));
        party.autoClose(NOW);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.autoClose(NOW.plus(Duration.ofHours(1))));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_CLOSED);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getVersion()).isEqualTo(1L);
        assertThat(party.getMembers()).extracting(PartyMember::getNoShowAt).containsOnly(NOW);
    }

    @DisplayName("autoClose - 돌려준 노쇼 목록은 바꿀 수 없다")
    @Test
    void autoClose_immutableResult() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        AutoCloseResult result = party.autoClose(NOW);
        Throwable thrown = catchThrowable(() -> result.noShowMemberIds().add(THIRD_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    // ───────────────────────── 공통 도우미 ─────────────────────────

    private static final List<Long> JOIN_ORDER = List.of(HOST_ID, MEMBER_ID, THIRD_ID);

    /** 도착 전 멤버. memberId 순서대로 합류했다 */
    private static PartyMember member(Long memberId) {
        return PartyMember.load(memberId, joinedAt(memberId));
    }

    /** 약속 시간 30초 뒤 도착한 멤버 */
    private static PartyMember arrivedMember(Long memberId) {
        return PartyMember.load(memberId, joinedAt(memberId), ARRIVED_AT, null, null);
    }

    private static Instant joinedAt(Long memberId) {
        return MEETING_AT.minus(Duration.ofMinutes(30)).plusSeconds(JOIN_ORDER.indexOf(memberId));
    }

    /** 방장이 MEETING_AT 으로 약속을 확정한 실시간 방. 첫 멤버가 방장 */
    private static Party confirmedRealtime(PartyStatus status, PartyMember... members) {
        return realtime(status, MEETING_AT, members);
    }

    /** departureAt 이 null 이면 약속을 정하지 않은 실시간 방 */
    private static Party realtime(PartyStatus status, Instant departureAt, PartyMember... members) {
        return Party.load(100L, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3),
                new ArrayList<>(List.of(members)), members[0].getMemberId(), MEETING_AT.minus(Duration.ofHours(1)), status,
                12000, 25, "encoded-path", 0L, RoomType.REALTIME, departureAt, null);
    }

    /** 출발 시각이 MEETING_AT 인 예약 방. 첫 멤버가 방장 */
    private static Party reserved(PartyStatus status, PartyMember... members) {
        return Party.load(100L, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3),
                new ArrayList<>(List.of(members)), members[0].getMemberId(), MEETING_AT.minus(Duration.ofDays(1)), status,
                null, null, null, 0L, RoomType.RESERVED, MEETING_AT, null);
    }

    private static PartyMember memberOf(Party party, Long memberId) {
        return party.getMembers().stream()
                .filter(m -> m.getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow();
    }

    /** 거절된 정리는 상태·version·노쇼를 건드리지 않는다 */
    private static void assertRejected(Throwable thrown, ErrorCode expected, Party party, PartyStatus status) {
        assertBusinessException(thrown, expected);
        assertThat(party.getStatus()).isEqualTo(status);
        assertThat(party.getVersion()).isZero();
        assertThat(party.getMembers()).noneMatch(m -> m.getNoShowAt() != null);
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
