package team.codingforest.moyeota.matching.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyTest {
    private static final Long 생성자 = 1L;
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
        return Party.open(생성자, 강남역, 판교역, 강남역출발, 판교역도착, new Capacity(capacity), 지금, 기본반경, 기본반경, 예상요금, 예상시간, 경로);
    }

    /** 정원 2명을 채우고 매칭까지 시작한 방 */
    private Party matchingParty() {
        Party party = openParty(2);
        party.join(참여자);
        party.startMatching(지금);
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

        assertThatThrownBy(() -> party.join(3L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_CLOSED);
    }

    @Test
    void 같은_회원은_중복_참여할_수_없다() {
        Party party = openParty(2);

        assertThatThrownBy(() -> party.join(생성자))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.ALREADY_JOINED_PARTY);
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

        party.startMatching(지금);

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
    void 마지막_남은_사람이_나가면_방이_취소되고_명단이_빈다() {
        Party party = openParty(2);

        party.leave(생성자);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getMembers()).isEmpty();   // 나간 사람이 취소된 방에 묶여 있으면 안 된다
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

        assertThatThrownBy(() -> party.leave(참여자))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_PARTY_MEMBER);
    }

    @Test
    void 취소된_방에서는_다시_나갈_수_없다() {
        // 나가기 이중 탭 - 이미 명단에서 빠졌으므로 "참여하지 않은 방" 예외가 난다
        Party party = openParty(2);
        party.leave(생성자);   // 혼자 나가며 방 취소 + 명단에서 제거됨

        assertThatThrownBy(() -> party.leave(생성자))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_PARTY_MEMBER);
    }


    // ───────────────────────── 매칭 시작 ─────────────────────────

    @Test
    void 정원이_찬_방은_매칭을_시작할_수_있다() {
        Party party = openParty(2);
        party.join(참여자);

        party.startMatching(지금);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.MATCHING);
    }

    @Test
    void 정원이_차지_않은_방은_매칭을_시작할_수_없다() {
        Party party = openParty(3);
        party.join(참여자);

        assertThatThrownBy(() -> party.startMatching(지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_COMPLETED);
    }

    @Test
    void 이미_매칭중인_방은_다시_시작할_수_없다() {
        Party party = matchingParty();

        assertThatThrownBy(() -> party.startMatching(지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_COMPLETED);
    }

    @Test
    void 매칭을_시작하면_시작_시각이_기록된다() {
        // 스위퍼의 타임아웃·반경 계산 기준 - 기록이 안 되면 방이 즉시 해산된다
        Party party = openParty(2);
        party.join(참여자);

        party.startMatching(지금);

        assertThat(party.getMatchingStartedAt()).isEqualTo(지금);
    }

    @Test
    void 매칭중인_방은_나갈_수_없다() {
        Party party = matchingParty();

        assertThatThrownBy(() -> party.leave(참여자))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_RECRUITING);
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

        assertThatThrownBy(() -> party.assignDriver(기사))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_MATCHING);
    }

    @Test
    void 이미_기사가_배정된_방에는_다시_배정할_수_없다() {
        Party party = assignedParty();

        assertThatThrownBy(() -> party.assignDriver(다른기사))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_MATCHING);
        assertThat(party.getTaxiDriverId()).isEqualTo(기사);   // 먼저 수락한 기사가 유지된다
    }

    @Test
    void 기사가_배정된_방은_나갈_수_없다() {
        Party party = assignedParty();

        assertThatThrownBy(() -> party.leave(참여자))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_RECRUITING);
    }

    @Test
    void 기사가_배정된_방은_해산할_수_없다() {
        Party party = assignedParty();

        assertThatThrownBy(party::failMatching)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_MATCHING);
    }

    // ───────────────────────── 픽업 대기 (도착 통보 가능 구간) ─────────────────────────

    @Test
    void 배정된_기사는_픽업_대기중이다() {
        Party party = assignedParty();

        assertThat(party.isAwaitingPickup(기사)).isTrue();
    }

    @Test
    void 배정되지_않은_기사는_픽업_대기가_아니다() {
        Party party = assignedParty();

        assertThat(party.isAwaitingPickup(다른기사)).isFalse();
    }

    @Test
    void 기사_배정_전에는_픽업_대기가_아니다() {
        // 배정 전 taxiDriverId가 null - NPE 없이 false여야 한다
        Party party = matchingParty();

        assertThat(party.isAwaitingPickup(기사)).isFalse();
    }

    @Test
    void 운행이_시작된_방은_픽업_대기가_아니다() {
        // 이미 태우고 달리는 중 - "기사님 도착" 알림이 가면 안 된다
        Party party = ridingParty();

        assertThat(party.isAwaitingPickup(기사)).isFalse();
    }

    @Test
    void 운행이_끝난_방은_픽업_대기가_아니다() {
        // taxiDriverId는 이력으로 남지만 도착 통보는 무효여야 한다
        Party party = ridingParty();
        party.completeRide(기사, 15000);

        assertThat(party.isAwaitingPickup(기사)).isFalse();
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

        assertThatThrownBy(() -> party.startRide(다른기사))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_ASSIGNED_DRIVER);
    }

    @Test
    void 기사_배정_전에는_탑승을_확정할_수_없다() {
        // 배정 전 taxiDriverId가 null - NPE가 아니라 도메인 예외가 나야 한다
        Party party = matchingParty();

        assertThatThrownBy(() -> party.startRide(기사))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_ASSIGNED_DRIVER);
    }

    @Test
    void 운행중인_방은_나갈_수_없다() {
        Party party = ridingParty();

        assertThatThrownBy(() -> party.leave(참여자))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_RECRUITING);
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

        assertThatThrownBy(() -> party.completeRide(다른기사, 15000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_ASSIGNED_DRIVER);
    }

    @Test
    void 탑승_확정_전에는_운행을_종료할_수_없다() {
        Party party = assignedParty();

        assertThatThrownBy(() -> party.completeRide(기사, 15000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_RIDING);
    }

    @Test
    void 종료된_방은_다시_매칭을_시작할_수_없다() {
        // FINISHED가 COMPLETED로 오인되면 종료된 방이 재매칭되는 좀비 방이 된다
        Party party = ridingParty();
        party.completeRide(기사, 15000);

        assertThatThrownBy(() -> party.startMatching(지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_COMPLETED);
    }

    // ───────────────────────── 매칭 실패 (해산) ─────────────────────────

    @Test
    void 기사를_못_구하면_방이_해산된다() {
        Party party = matchingParty();

        party.failMatching();

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getMembers()).isNotEmpty();   // 해산 알림 수신자 명단은 남긴다
    }

    @Test
    void 해산된_방은_다시_매칭을_시작할_수_없다() {
        Party party = matchingParty();
        party.failMatching();

        assertThatThrownBy(() -> party.startMatching(지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_COMPLETED);
    }

    // ───────────────────────── 정원 충족 시각 ─────────────────────────

    @Test
    void 정원이_차면_충족_시각이_찍힌다() {
        Party party = openParty(2);
        assertThat(party.getCompletedAt()).as("모집 중엔 없다").isNull();

        party.join(참여자);

        assertThat(party.getCompletedAt()).isNotNull();
    }

    @Test
    void 혼자_타는_방은_생성_시점이_충족_시각이다() {
        Party party = openParty(1);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getCompletedAt()).isNotNull();
    }

    @Test
    void 누가_나가서_모집으로_돌아가면_충족_시각이_지워진다() {
        Party party = openParty(2);
        party.join(참여자);

        party.leave(참여자);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.getCompletedAt()).as("다시 차면 새 시각이 찍혀야 하므로 옛 값을 남기면 안 된다").isNull();
    }

    // ───────────────────────── 기사 없는 종료 (배포 모드) ─────────────────────────

    @Test
    void 정원이_찬_방은_참여자가_직접_합승_완료할_수_있다() {
        Party party = openParty(2);
        party.join(참여자);

        party.finishWithoutDriver(참여자);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
    }

    @Test
    void 합승_완료해도_정원_충족_시각은_유지된다() {
        Party party = openParty(2);
        party.join(참여자);
        Instant 충족시각 = party.getCompletedAt();

        party.finishWithoutDriver(생성자);

        assertThat(party.getCompletedAt()).as("\"정원이 찬 시각\"이지 \"끝난 시각\"이 아니다 - 덮어쓰면 기록이 사라진다").isEqualTo(충족시각);
    }

    @Test
    void 방에_없는_사람은_합승_완료할_수_없다() {
        Party party = openParty(2);
        party.join(참여자);

        assertThatThrownBy(() -> party.finishWithoutDriver(다른기사))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_PARTY_MEMBER);
    }

    @Test
    void 모집_중인_방은_합승_완료할_수_없다() {
        Party party = openParty(3);
        party.join(참여자);   // 2/3 - 아직 ACTIVE

        assertThatThrownBy(() -> party.finishWithoutDriver(생성자))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_COMPLETED);
    }

    @Test
    void 기사가_배정된_방은_기사_없는_종료를_할_수_없다() {
        Party party = assignedParty();   // DRIVER_ASSIGNED - COMPLETED 가 아니라 여기서 먼저 걸린다

        assertThatThrownBy(() -> party.finishWithoutDriver(생성자))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_COMPLETED);
    }

    @Test
    void 방치된_방은_시스템이_멤버_검증_없이_닫는다() {
        Party party = openParty(2);
        party.join(참여자);

        party.expireCompleted();

        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
    }

    @Test
    void 이미_닫힌_방은_다시_닫을_수_없다() {
        Party party = openParty(2);
        party.join(참여자);
        party.finishWithoutDriver(참여자);

        assertThatThrownBy(party::expireCompleted)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_NOT_COMPLETED);
    }
}
