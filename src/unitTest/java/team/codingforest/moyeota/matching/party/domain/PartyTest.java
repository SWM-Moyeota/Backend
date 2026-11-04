package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartyTest {
    private static final Long 생성자 = 1L;
    private static final Long 참여자 = 2L;
    private static final Long 외부인 = 10L;
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

    // ───────────────────────── 합승 완료 ─────────────────────────

    @Test
    void 정원이_찬_방은_방장이_합승_완료할_수_있다() {
        Party party = openParty(2);
        party.join(참여자);

        party.finishWithoutDriver(생성자);

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

        assertThatThrownBy(() -> party.finishWithoutDriver(외부인))
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
    void 방치된_방은_시스템이_멤버_검증_없이_닫는다() {
        Party party = openParty(2);
        party.join(참여자);

        party.autoClose(Instant.now());

        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
    }

    @Test
    void 이미_닫힌_방은_다시_닫을_수_없다() {
        Party party = openParty(2);
        party.join(참여자);
        party.finishWithoutDriver(생성자);

        assertThatThrownBy(() -> party.autoClose(Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.PARTY_CLOSED);
    }

    // ───────────────────────── 방장 ─────────────────────────

    /** 합류 시각을 조작해 복원한 정원 충족 방 - 도메인 경로로는 joinedAt 이 항상 Instant.now() 로 찍힌다 */
    private Party completedPartyWith(PartyMember... members) {
        return Party.load(1L, 강남역, 판교역, 기본반경, 기본반경, 강남역출발, 판교역도착, new Capacity(members.length),
                List.of(members), 지금, PartyStatus.COMPLETED, 예상요금, 예상시간, 경로, 지금, 0L);
    }

    @Test
    void 방장은_가장_먼저_합류한_멤버다() {
        Party party = openParty(3);
        party.join(참여자);

        assertThat(party.hostId()).isEqualTo(생성자);
    }

    @Test
    void 방장이_나가면_다음으로_일찍_합류한_멤버가_방장이_된다() {
        Party party = openParty(3);
        party.join(참여자);

        party.leave(생성자);

        assertThat(party.hostId()).isEqualTo(참여자);
    }

    @Test
    void 합류_시각이_같으면_memberId가_작은_멤버가_방장이다() {
        Party party = completedPartyWith(PartyMember.load(5L, 지금), PartyMember.load(3L, 지금));

        assertThat(party.hostId()).isEqualTo(3L);
    }

    @Test
    void 멤버가_없는_방에는_방장이_없다() {
        Party party = openParty(2);
        party.leave(생성자);   // 마지막 멤버가 나가 명단이 빈다

        assertThat(party.hostId()).isNull();
        assertThat(party.isHost(생성자)).isFalse();
    }

    @Test
    void 방장이_아닌_멤버는_합승_완료할_수_없다() {
        Party party = openParty(2);
        party.join(참여자);

        assertThatThrownBy(() -> party.finishWithoutDriver(참여자))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NOT_PARTY_HOST);

        assertThat(party.getStatus()).as("거부된 호출이 방을 닫으면 안 된다").isEqualTo(PartyStatus.COMPLETED);
    }

    @Test
    void 승계된_방장은_합승_완료할_수_있다() {
        // 원래 방장이 빠진 뒤 복원된 방 - 창설자가 아니라 "현재 멤버 중 가장 일찍 합류한 사람"이 권한을 가진다
        Party party = completedPartyWith(PartyMember.load(참여자, 지금), PartyMember.load(생성자, 지금.plusSeconds(60)));

        party.finishWithoutDriver(참여자);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
    }

    // ───────────────────────── version ─────────────────────────

    @Test
    void 새_방의_version_은_0_이다() {
        assertThat(openParty(3).getVersion()).isZero();
    }

    @Test
    void 참여_나가기_합승완료는_version_을_1씩_올린다() {
        Party party = openParty(3);

        party.join(참여자);
        assertThat(party.getVersion()).isEqualTo(1);

        party.leave(참여자);
        assertThat(party.getVersion()).isEqualTo(2);

        party.join(참여자);
        party.join(3L);
        assertThat(party.getVersion()).isEqualTo(4);

        party.finishWithoutDriver(생성자);
        assertThat(party.getVersion()).isEqualTo(5);
    }

    @Test
    void 정원이_차는_참여도_version_은_1만_오른다() {
        Party party = openParty(2);

        party.join(참여자);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getVersion()).isEqualTo(1);
    }

    @Test
    void 마지막_사람이_나가_방이_닫혀도_version_은_1만_오른다() {
        Party party = openParty(2);

        party.leave(생성자);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getVersion()).isEqualTo(1);
    }

    @Test
    void 방치된_방의_자동_종료는_version_을_1_올린다() {
        Party party = openParty(2);
        party.join(참여자);

        party.autoClose(Instant.now());

        assertThat(party.getVersion()).isEqualTo(2);
    }

    @Test
    void 거부된_명령은_version_을_바꾸지_않는다() {
        Party party = openParty(2);
        party.join(참여자);

        assertThatThrownBy(() -> party.join(3L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> party.leave(3L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> party.finishWithoutDriver(참여자)).isInstanceOf(BusinessException.class);

        assertThat(party.getVersion()).isEqualTo(1);
    }
}
