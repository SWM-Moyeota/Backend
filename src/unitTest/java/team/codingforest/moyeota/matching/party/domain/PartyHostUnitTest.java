package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  방장(host_id) 저장·승계 규칙
 */
class PartyHostUnitTest {
    private static final Long CREATOR_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long THIRD_ID = 3L;
    private static final Long OUTSIDER_ID = 99L;
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);
    private static final Instant BASE_TIME = Instant.parse("2026-10-01T00:00:00Z");

    private static Party create(int capacity) {
        return Party.create(CREATOR_ID, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(capacity), Instant.now(),
                12000, 25, "encoded-path");
    }

    /** 저장된 host_id 와 합류 시각을 그대로 복원한 모집 중인 방 */
    private static Party restored(Long hostId, PartyMember... members) {
        return Party.load(100L, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3),
                List.of(members), hostId, BASE_TIME, PartyStatus.ACTIVE, 12000, 25, "encoded-path", 0L,
                RoomType.REALTIME, null, null);
    }

    private static PartyMember joinedAt(Long memberId, long secondsAfterBase) {
        return PartyMember.load(memberId, BASE_TIME.plusSeconds(secondsAfterBase));
    }

    // ───────────────────────── 생성 ─────────────────────────

    @DisplayName("open - 방을 만든 사람이 방장이다")
    @Test
    void create_creatorIsHost() {

        /*
         * test
         */
        Party party = create(3);

        /*
         * verify
         */
        assertThat(party.hostId()).isEqualTo(CREATOR_ID);
        assertThat(party.isHost(CREATOR_ID)).isTrue();
    }

    @DisplayName("reserve - 예약 방을 만든 사람이 방장이다")
    @Test
    void reserve_creatorIsHost() {

        /*
         * settings
         */
        Instant now = Instant.now();

        /*
         * test
         */
        Party party = Party.reserve(CREATOR_ID, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3), now, now.plus(Duration.ofDays(1)));

        /*
         * verify
         */
        assertThat(party.hostId()).isEqualTo(CREATOR_ID);
        assertThat(party.isHost(CREATOR_ID)).isTrue();
    }

    // ───────────────────────── 참여 ─────────────────────────

    @DisplayName("join - 새로 들어온 멤버는 방장이 아니고 방장은 그대로다")
    @Test
    void join_keepsHost() {

        /*
         * settings
         */
        Party party = create(3);

        /*
         * test
         */
        party.join(MEMBER_ID);

        /*
         * verify
         */
        assertThat(party.hostId()).isEqualTo(CREATOR_ID);
        assertThat(party.isHost(MEMBER_ID)).isFalse();
    }

    // ───────────────────────── 나가기 ─────────────────────────

    @DisplayName("leave - 방장이 아닌 멤버가 나가면 방장은 그대로다")
    @Test
    void leave_nonHostKeepsHost() {

        /*
         * settings
         */
        Party party = restored(CREATOR_ID, joinedAt(CREATOR_ID, 0), joinedAt(MEMBER_ID, 10), joinedAt(THIRD_ID, 20));

        /*
         * test
         */
        party.leave(MEMBER_ID);

        /*
         * verify
         */
        assertThat(party.hostId()).isEqualTo(CREATOR_ID);
    }

    @DisplayName("leave - 방장이 나가면 남은 멤버 중 가장 먼저 합류한 멤버가 승계한다")
    @Test
    void leave_hostHandsOverToEarliestMember() {

        /*
         * settings
         */
        // 목록 순서·memberId 순서가 아니라 합류 시각으로 고르는지 보려고 THIRD_ID 를 MEMBER_ID 보다 먼저 합류시킨다
        Party party = restored(CREATOR_ID, joinedAt(CREATOR_ID, 0), joinedAt(MEMBER_ID, 60), joinedAt(THIRD_ID, 30));

        /*
         * test
         */
        party.leave(CREATOR_ID);

        /*
         * verify
         */
        assertThat(party.hostId()).isEqualTo(THIRD_ID);
        assertThat(party.isHost(CREATOR_ID)).isFalse();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
    }

    @DisplayName("leave - 방장이 나가고 남은 멤버의 합류 시각이 같으면 memberId 가 작은 멤버가 승계한다")
    @Test
    void leave_hostHandsOverBySmallerMemberIdOnTie() {

        /*
         * settings
         */
        Party party = restored(CREATOR_ID, joinedAt(CREATOR_ID, 0), joinedAt(THIRD_ID, 10), joinedAt(MEMBER_ID, 10));

        /*
         * test
         */
        party.leave(CREATOR_ID);

        /*
         * verify
         */
        assertThat(party.hostId()).isEqualTo(MEMBER_ID);
    }

    @DisplayName("leave - 마지막 멤버가 나가면 방이 취소되고 방장이 없다")
    @Test
    void leave_lastMemberCancels() {

        /*
         * settings
         */
        Party party = create(3);

        /*
         * test
         */
        party.leave(CREATOR_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.hostId()).isNull();
        assertThat(party.isHost(CREATOR_ID)).isFalse();
    }

    // ───────────────────────── 복원 ─────────────────────────

    @DisplayName("load - 저장된 방장이 가장 먼저 합류한 멤버가 아니어도 그대로 복원한다")
    @Test
    void load_restoresStoredHost() {

        /*
         * test
         */
        Party party = restored(MEMBER_ID, joinedAt(CREATOR_ID, 0), joinedAt(MEMBER_ID, 10));

        /*
         * verify
         */
        assertThat(party.hostId()).isEqualTo(MEMBER_ID);
        assertThat(party.isHost(CREATOR_ID)).isFalse();
    }

    @DisplayName("load - 저장된 방장이 비어 있으면 승계 규칙으로 정한다")
    @Test
    void load_nullHostFallsBackToEarliestMember() {

        /*
         * test
         */
        // 배포 중 구버전 서버가 host_id 없이 만든 방
        Party party = restored(null, joinedAt(MEMBER_ID, 10), joinedAt(CREATOR_ID, 0));

        /*
         * verify
         */
        assertThat(party.hostId()).isEqualTo(CREATOR_ID);
    }

    @DisplayName("load - 저장된 방장이 현재 멤버가 아니면 승계 규칙으로 정한다")
    @Test
    void load_departedHostFallsBackToEarliestMember() {

        /*
         * test
         */
        // 배포 중 구버전 서버가 방장 퇴장을 처리해 host_id 가 갱신되지 않은 방
        Party party = restored(OUTSIDER_ID, joinedAt(MEMBER_ID, 10), joinedAt(THIRD_ID, 20));

        /*
         * verify
         */
        assertThat(party.hostId()).isEqualTo(MEMBER_ID);
        assertThat(party.isHost(OUTSIDER_ID)).isFalse();
    }

    @DisplayName("load - 멤버가 없는 방은 방장이 없다")
    @Test
    void load_noMembersNoHost() {

        /*
         * test
         */
        Party party = restored(null);

        /*
         * verify
         */
        assertThat(party.hostId()).isNull();
        assertThat(party.isHost(CREATOR_ID)).isFalse();
    }

    @DisplayName("load - host_id 를 받지 않는 복원은 가장 먼저 합류한 멤버를 방장으로 정한다")
    @Test
    void load_legacyOverloadDerivesHost() {

        /*
         * test
         */
        Party party = Party.load(100L, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3),
                List.of(joinedAt(THIRD_ID, 20), joinedAt(MEMBER_ID, 10)), BASE_TIME, PartyStatus.ACTIVE, 12000, 25, "encoded-path",
                0L);

        /*
         * verify
         */
        assertThat(party.hostId()).isEqualTo(MEMBER_ID);
    }

    // ───────────────────────── 방장 판별 ─────────────────────────

    @DisplayName("isHost - 방장이 아닌 멤버와 방에 없는 사람, null 은 방장이 아니다")
    @Test
    void isHost_onlyHost() {

        /*
         * settings
         */
        Party party = restored(CREATOR_ID, joinedAt(CREATOR_ID, 0), joinedAt(MEMBER_ID, 10));

        /*
         * test
         */
        boolean host = party.isHost(CREATOR_ID);
        boolean member = party.isHost(MEMBER_ID);
        boolean outsider = party.isHost(OUTSIDER_ID);
        boolean nobody = party.isHost(null);

        /*
         * verify
         */
        assertThat(host).isTrue();
        assertThat(member).isFalse();
        assertThat(outsider).isFalse();
        assertThat(nobody).isFalse();
    }
}
