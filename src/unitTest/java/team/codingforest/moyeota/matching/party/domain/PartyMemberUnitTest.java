package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  멤버의 출석(도착·노쇼) 상태. 노쇼는 찍혔고 무효로 돌리지 않았을 때만 노쇼다
 */
class PartyMemberUnitTest {
    private static final Long MEMBER_ID = 2L;
    private static final Instant JOINED_AT = Instant.parse("2026-11-03T11:00:00Z");
    private static final Instant ARRIVED_AT = Instant.parse("2026-11-03T12:01:00Z");
    private static final Instant NO_SHOW_AT = Instant.parse("2026-11-03T12:05:00Z");
    private static final Instant VOIDED_AT = Instant.parse("2026-11-04T09:00:00Z");

    @DisplayName("load - 합류 시각만 주면 도착·노쇼 기록이 없다")
    @Test
    void load_withoutAttendance() {

        /*
         * test
         */
        PartyMember member = PartyMember.load(MEMBER_ID, JOINED_AT);

        /*
         * verify
         */
        assertThat(member.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(member.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(member.getArrivedAt()).isNull();
        assertThat(member.getNoShowAt()).isNull();
        assertThat(member.getNoShowVoidedAt()).isNull();
        assertThat(member.isArrived()).isFalse();
        assertThat(member.isNoShow()).isFalse();
    }

    @DisplayName("load - 저장된 도착·노쇼·무효 시각을 그대로 복원한다")
    @Test
    void load_withAttendance() {

        /*
         * test
         */
        PartyMember member = PartyMember.load(MEMBER_ID, JOINED_AT, ARRIVED_AT, NO_SHOW_AT, VOIDED_AT);

        /*
         * verify
         */
        assertThat(member.getArrivedAt()).isEqualTo(ARRIVED_AT);
        assertThat(member.getNoShowAt()).isEqualTo(NO_SHOW_AT);
        assertThat(member.getNoShowVoidedAt()).isEqualTo(VOIDED_AT);
    }

    @DisplayName("isNoShow - 노쇼로 찍혔고 무효로 돌리지 않았으면 노쇼다")
    @Test
    void isNoShow_effective() {

        /*
         * settings
         */
        PartyMember member = PartyMember.load(MEMBER_ID, JOINED_AT, null, NO_SHOW_AT, null);

        /*
         * test
         */
        boolean noShow = member.isNoShow();

        /*
         * verify
         */
        assertThat(noShow).isTrue();
    }

    @DisplayName("isNoShow - 운영자가 무효로 돌린 노쇼는 노쇼가 아니다")
    @Test
    void isNoShow_voided() {

        /*
         * settings
         */
        PartyMember member = PartyMember.load(MEMBER_ID, JOINED_AT, null, NO_SHOW_AT, VOIDED_AT);

        /*
         * test
         */
        boolean noShow = member.isNoShow();

        /*
         * verify
         */
        assertThat(noShow).isFalse();
    }

    @DisplayName("arrive - 처음 도착한 시각을 남기고, 다시 불러도 처음 시각을 그대로 둔다")
    @Test
    void arrive_keepsFirstTime() {

        /*
         * settings
         */
        PartyMember member = PartyMember.load(MEMBER_ID, JOINED_AT);

        /*
         * test
         */
        member.arrive(ARRIVED_AT);
        member.arrive(ARRIVED_AT.plusSeconds(120));

        /*
         * verify
         */
        assertThat(member.isArrived()).isTrue();
        assertThat(member.getArrivedAt()).isEqualTo(ARRIVED_AT);
    }

    @DisplayName("markNoShow - 노쇼 시각을 남기고 노쇼가 된다")
    @Test
    void markNoShow() {

        /*
         * settings
         */
        PartyMember member = PartyMember.load(MEMBER_ID, JOINED_AT);

        /*
         * test
         */
        member.markNoShow(NO_SHOW_AT);

        /*
         * verify
         */
        assertThat(member.getNoShowAt()).isEqualTo(NO_SHOW_AT);
        assertThat(member.isNoShow()).isTrue();
        assertThat(member.isArrived()).isFalse();
    }
}
