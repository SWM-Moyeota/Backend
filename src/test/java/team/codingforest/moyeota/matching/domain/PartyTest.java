package team.codingforest.moyeota.matching.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class PartyTest {
    private static final Long 방장 = 1L;
    private static final Long 참여자 = 2L;
    private static final Long 기사 = 9L;
    private static final Long 다른기사 = 10L;
    private static final Location 강남역 = new Location(37.4979, 127.0276);
    private static final Location 판교역 = new Location(37.3948, 127.1112);
    private static final String 강남역출발 = "강남역";
    private static final String 판교역도착 = "판교역";
    private static final Radius 기본반경 = new Radius(100);
    private static final Instant 지금 = Instant.now();
    private static final Integer 예상요금 = 12000;
    private static final Integer 예상시간 = 25;
    private static final String 경로 = "_p~iF~ps|U_ulLnnqC";

    private Party openParty(int capacity) {
        return Party.open(방장, 강남역, 판교역, 강남역출발, 판교역도착, new Capacity(capacity), 지금, 기본반경, 기본반경, 예상요금, 예상시간, 경로);
    }

    /** 정원 2명을 채우고 매칭까지 시작한 방 */
    private Party matchingParty() {
        Party party = openParty(2);
        party.join(참여자);
        party.startMatching();
        return party;
    }

    /** 기사까지 배정된 방 */
    private Party assignedParty() {
        Party party = matchingParty();
        party.assignDriver(기사);
        return party;
    }

    /** 운행 중인 방 */
    private Party ridingParty() {
        Party party = assignedParty();
        party.startRide(기사);
        return party;
    }

    // ───────────────────────── 모집 ─────────────────────────

    @Test
    void 정원이_차면_자동으로_마감된다() {
        Party party = openParty(2);

        party.join(참여자);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @Test
    void 마감된_방에는_참여할_수_없다() {
        Party party = openParty(2);
        party.join(참여자);

        assertThatThrownBy(() -> party.join(3L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_회원은_중복_참여할_수_없다() {
        Party party = openParty(2);

        assertThatThrownBy(() -> party.join(방장)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 혼자_타는_방은_생성_즉시_마감된다() {
        // 1인 방은 join 경로를 탈 수 없으므로 생성 시점에 정원 충족 처리가 안 되면 좀비 방이 된다
        Party party = openParty(1);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @Test
    void 혼자_타는_방은_바로_매칭을_시작할_수_있다() {
        Party party = openParty(1);

        party.startMatching();

        assertThat(party.getStatus()).isEqualTo(PartyStatus.MATCHING);
    }

    @Test
    void 참여자가_나가면_명단에서_빠진다() {
        Party party = openParty(3);
        party.join(참여자);

        party.leave(참여자);

        assertThat(party.hasMember(참여자)).isFalse();
        assertThat(party.getMembers()).hasSize(1);
    }

    @Test
    void 방장이_나가면_가장_먼저_들어온_사람이_방장이_된다() {
        Party party = openParty(3);
        party.join(2L);
        party.join(3L);

        party.leave(방장);

        assertThat(party.getHostId()).isEqualTo(2L);
        assertThat(party.hasMember(방장)).isFalse();
    }

    @Test
    void 마지막_남은_사람이_나가면_방이_취소된다() {
        Party party = openParty(2);

        party.leave(방장);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 마감된_방에서_누군가_나가면_다시_모집중이_된다() {
        Party party = openParty(2);
        party.join(참여자);

        party.leave(참여자);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
    }

    @Test
    void 참여하지_않은_회원은_나갈_수_없다() {
        Party party = openParty(2);

        assertThatThrownBy(() -> party.leave(참여자)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 방장은_방을_취소할_수_있다() {
        Party party = openParty(3);
        party.join(참여자);

        party.cancel(방장);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 방장이_아니면_취소할_수_없다() {
        Party party = openParty(2);
        party.join(참여자);

        assertThatThrownBy(() -> party.cancel(참여자)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 마감된_방도_방장은_취소할_수_있다() {
        Party party = openParty(2);
        party.join(참여자);

        party.cancel(방장);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    // ───────────────────────── 매칭 시작 ─────────────────────────

    @Test
    void 정원이_찬_방은_매칭을_시작할_수_있다() {
        Party party = openParty(2);
        party.join(참여자);

        party.startMatching();

        assertThat(party.getStatus()).isEqualTo(PartyStatus.MATCHING);
    }

    @Test
    void 정원이_차지_않은_방은_매칭을_시작할_수_없다() {
        Party party = openParty(3);
        party.join(참여자);

        assertThatThrownBy(party::startMatching).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 이미_매칭중인_방은_다시_시작할_수_없다() {
        Party party = matchingParty();

        assertThatThrownBy(party::startMatching).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 매칭중인_방은_나갈_수_없다() {
        Party party = matchingParty();

        assertThatThrownBy(() -> party.leave(참여자)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 매칭중인_방은_폭파할_수_없다() {
        Party party = matchingParty();

        assertThatThrownBy(() -> party.cancel(방장)).isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────────────────── 기사 배정 ─────────────────────────

    @Test
    void 매칭중인_방에_기사를_배정하면_DRIVER_ASSIGNED가_된다() {
        Party party = matchingParty();

        party.assignDriver(기사);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.DRIVER_ASSIGNED);
        assertThat(party.getTaxiDriverId()).isEqualTo(기사);
    }

    @Test
    void 매칭중이_아니면_기사를_배정할_수_없다() {
        Party party = openParty(2);
        party.join(참여자);   // COMPLETED

        assertThatThrownBy(() -> party.assignDriver(기사)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 이미_기사가_배정된_방에는_다시_배정할_수_없다() {
        Party party = assignedParty();

        assertThatThrownBy(() -> party.assignDriver(다른기사)).isInstanceOf(IllegalArgumentException.class);
        assertThat(party.getTaxiDriverId()).isEqualTo(기사);   // 먼저 수락한 기사가 유지된다
    }

    @Test
    void 기사가_배정된_방은_나갈_수_없다() {
        Party party = assignedParty();

        assertThatThrownBy(() -> party.leave(참여자)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기사가_배정된_방은_매칭을_되돌릴_수_없다() {
        Party party = assignedParty();

        assertThatThrownBy(party::cancelMatching).isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────────────────── 운행 ─────────────────────────

    @Test
    void 배정된_기사가_탑승을_확정하면_IN_RIDE가_된다() {
        Party party = assignedParty();

        party.startRide(기사);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.IN_RIDE);
    }

    @Test
    void 배정되지_않은_기사는_탑승을_확정할_수_없다() {
        Party party = assignedParty();

        assertThatThrownBy(() -> party.startRide(다른기사)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기사_배정_전에는_탑승을_확정할_수_없다() {
        // 배정 전 taxiDriverId가 null - NPE가 아니라 도메인 예외가 나야 한다
        Party party = matchingParty();

        assertThatThrownBy(() -> party.startRide(기사)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 운행중인_방은_나갈_수_없다() {
        Party party = ridingParty();

        assertThatThrownBy(() -> party.leave(참여자)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 배정된_기사가_운행을_종료하면_FINISHED가_된다() {
        Party party = ridingParty();

        party.completeRide(기사, 15000);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
    }

    @Test
    void 배정되지_않은_기사는_운행을_종료할_수_없다() {
        Party party = ridingParty();

        assertThatThrownBy(() -> party.completeRide(다른기사, 15000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 탑승_확정_전에는_운행을_종료할_수_없다() {
        Party party = assignedParty();

        assertThatThrownBy(() -> party.completeRide(기사, 15000)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 종료된_방은_다시_매칭을_시작할_수_없다() {
        // FINISHED가 COMPLETED로 오인되면 종료된 방이 재매칭되는 좀비 방이 된다
        Party party = ridingParty();
        party.completeRide(기사, 15000);

        assertThatThrownBy(party::startMatching).isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────────────────── 매칭 복귀 ─────────────────────────

    @Test
    void 기사를_못_구하면_모집완료_상태로_되돌아간다() {
        Party party = matchingParty();

        party.cancelMatching();

        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @Test
    void 되돌아간_방은_다시_매칭을_시작할_수_있다() {
        Party party = matchingParty();
        party.cancelMatching();

        party.startMatching();

        assertThat(party.getStatus()).isEqualTo(PartyStatus.MATCHING);
    }
}
