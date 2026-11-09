package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  멤버의 출석은 도착 하나다. 도착한 멤버가 탄 사람이고 정산 대상이다
 */
class PartyMemberUnitTest {
    private static final Long MEMBER_ID = 2L;
    private static final Instant JOINED_AT = Instant.parse("2026-11-03T11:00:00Z");
    private static final Instant ARRIVED_AT = Instant.parse("2026-11-03T12:01:00Z");

    @DisplayName("load - 합류 시각만 주면 도착 기록이 없다")
    @Test
    void load_withoutArrival() {

        /*
         * test
         */
        PartyMember member = PartyMember.load(MEMBER_ID, JOINED_AT, null);

        /*
         * verify
         */
        assertThat(member.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(member.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(member.getArrivedAt()).isNull();
        assertThat(member.isArrived()).isFalse();
    }

    @DisplayName("load - 저장된 도착 시각을 그대로 복원한다")
    @Test
    void load_withArrival() {

        /*
         * test
         */
        PartyMember member = PartyMember.load(MEMBER_ID, JOINED_AT, ARRIVED_AT);

        /*
         * verify
         */
        assertThat(member.getArrivedAt()).isEqualTo(ARRIVED_AT);
        assertThat(member.isArrived()).isTrue();
    }

    @DisplayName("arrive - 처음 도착한 시각을 남기고, 다시 불러도 처음 시각을 그대로 둔다")
    @Test
    void arrive_keepsFirstTime() {

        /*
         * settings
         */
        PartyMember member = PartyMember.load(MEMBER_ID, JOINED_AT, null);

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
}
