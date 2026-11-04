package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 *  노쇼 이의 신청 자격(ensureNoShow)과 운영자의 노쇼 무효 처리(voidNoShow)
 */
class PartyNoShowUnitTest {
    private static final Long HOST_ID = 1L;
    private static final Long NO_SHOW_ID = 2L;
    private static final Long VOIDED_ID = 3L;
    private static final Long OUTSIDER_ID = 99L;
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);
    private static final Instant MEETING_AT = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant NO_SHOW_AT = MEETING_AT.plus(Duration.ofMinutes(10));
    private static final Instant VOIDED_AT = MEETING_AT.plus(Duration.ofDays(1));
    private static final Instant NOW = MEETING_AT.plus(Duration.ofDays(2));

    // ───────────────────────── ensureNoShow ─────────────────────────

    @DisplayName("ensureNoShow - 노쇼로 찍힌 멤버는 통과한다")
    @Test
    void ensureNoShow() {

        /*
         * settings
         */
        Party party = finishedParty();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.ensureNoShow(NO_SHOW_ID));

        /*
         * verify
         */
        assertThat(thrown).isNull();
    }

    @DisplayName("ensureNoShow - 도착해 탄 멤버는 NOT_NO_SHOW")
    @Test
    void ensureNoShow_arrived() {

        /*
         * settings
         */
        Party party = finishedParty();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.ensureNoShow(HOST_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_NO_SHOW);
    }

    @DisplayName("ensureNoShow - 이미 무효로 돌아간 노쇼는 NOT_NO_SHOW")
    @Test
    void ensureNoShow_voided() {

        /*
         * settings
         */
        Party party = finishedParty();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.ensureNoShow(VOIDED_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_NO_SHOW);
    }

    @DisplayName("ensureNoShow - 방의 멤버가 아니면 NOT_PARTY_MEMBER")
    @Test
    void ensureNoShow_notMember() {

        /*
         * settings
         */
        Party party = finishedParty();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.ensureNoShow(OUTSIDER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_PARTY_MEMBER);
    }

    // ───────────────────────── voidNoShow ─────────────────────────

    @DisplayName("voidNoShow - 노쇼에 무효 시각을 남겨 노쇼가 아니게 하고, version·상태·다른 멤버는 그대로 둔다")
    @Test
    void voidNoShow() {

        /*
         * settings
         */
        Party party = finishedParty();

        /*
         * test
         */
        boolean voided = party.voidNoShow(NO_SHOW_ID, NOW);

        /*
         * verify
         */
        assertThat(voided).isTrue();
        PartyMember member = memberOf(party, NO_SHOW_ID);
        assertThat(member.getNoShowVoidedAt()).isEqualTo(NOW);
        assertThat(member.getNoShowAt()).as("노쇼 시각은 기록으로 남는다").isEqualTo(NO_SHOW_AT);
        assertThat(member.isNoShow()).isFalse();
        assertThat(party.getVersion()).isEqualTo(7L);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(memberOf(party, VOIDED_ID).getNoShowVoidedAt()).isEqualTo(VOIDED_AT);
    }

    @DisplayName("voidNoShow - 이미 무효였으면 처음 시각을 두고 false")
    @Test
    void voidNoShow_alreadyVoided() {

        /*
         * settings
         */
        Party party = finishedParty();

        /*
         * test
         */
        boolean voided = party.voidNoShow(VOIDED_ID, NOW);

        /*
         * verify
         */
        assertThat(voided).isFalse();
        assertThat(memberOf(party, VOIDED_ID).getNoShowVoidedAt()).isEqualTo(VOIDED_AT);
    }

    @DisplayName("voidNoShow - 노쇼로 찍힌 적 없는 멤버는 NOT_NO_SHOW")
    @Test
    void voidNoShow_neverNoShow() {

        /*
         * settings
         */
        Party party = finishedParty();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.voidNoShow(HOST_ID, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_NO_SHOW);
        assertThat(memberOf(party, HOST_ID).getNoShowVoidedAt()).isNull();
    }

    @DisplayName("voidNoShow - 방의 멤버가 아니면 NOT_PARTY_MEMBER")
    @Test
    void voidNoShow_notMember() {

        /*
         * settings
         */
        Party party = finishedParty();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.voidNoShow(OUTSIDER_ID, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_PARTY_MEMBER);
    }

    // ───────────────────────── 도우미 ─────────────────────────

    /** 방장은 도착해 탔고, NO_SHOW_ID 는 노쇼, VOIDED_ID 는 이미 무효로 돌아간 노쇼인 끝난 확정 방 */
    private static Party finishedParty() {
        List<PartyMember> members = new ArrayList<>(List.of(
                PartyMember.load(HOST_ID, MEETING_AT.minus(Duration.ofMinutes(30)), MEETING_AT, null, null),
                PartyMember.load(NO_SHOW_ID, MEETING_AT.minus(Duration.ofMinutes(20)), null, NO_SHOW_AT, null),
                PartyMember.load(VOIDED_ID, MEETING_AT.minus(Duration.ofMinutes(10)), null, NO_SHOW_AT, VOIDED_AT)));

        return Party.load(100L, DEPARTURE, DESTINATION, Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역", new Capacity(3), members,
                HOST_ID, MEETING_AT.minus(Duration.ofHours(1)), PartyStatus.FINISHED, 12000, 25, "encoded-path",
                null, null, null, 7L, RoomType.REALTIME, MEETING_AT, null);
    }

    private static PartyMember memberOf(Party party, Long memberId) {
        return party.getMembers().stream()
                .filter(m -> m.getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow();
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
