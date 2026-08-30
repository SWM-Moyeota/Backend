package team.codingforest.moyeota.dispatch.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.api.MatchingTarget;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class MatchingSweeperTest {

    private static final Long 방번호 = 1L;
    private static final Long 다른방번호 = 2L;
    private static final PartySummary 강남출발방 =
            new PartySummary(방번호, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 2, 12000, 25, null);
    private static final PartySummary 판교출발방 =
            new PartySummary(다른방번호, 37.3948, 127.1112, 37.4979, 127.0276, "판교역", "강남역", 3, 15000, 30, null);

    private FakeDriverLocations locations;
    private RecordingNotifier notifier;
    private InMemoryCallCandidates candidates;
    private FakePartyAccess partyAccess;
    private MatchingSweeper sweeper;

    @BeforeEach
    void setUp() {
        locations = new FakeDriverLocations();
        notifier = new RecordingNotifier();
        candidates = new InMemoryCallCandidates();
        partyAccess = new FakePartyAccess(강남출발방, 판교출발방);

        DispatchService dispatchService = new DispatchService(partyAccess, new FakeDriverAccess(Set.of(1L, 2L, 3L)), locations, notifier, candidates);
        sweeper = new MatchingSweeper(partyAccess, dispatchService, candidates, notifier);
    }

    /** 매칭 시작 후 elapsed 만큼 지난 방을 스윕 대상에 올린다 */
    private void 매칭중(Long partyId, Duration elapsed) {
        partyAccess.matchingTargets.add(new MatchingTarget(partyId, Instant.now().minus(elapsed)));
    }

    @Test
    void 타임아웃_전이면_경과_시간에_맞는_반경으로_재탐색한다() {
        매칭중(방번호, Duration.ofSeconds(60));
        locations.nearby = List.of(1L);

        sweeper.sweep();

        assertThat(locations.lastRadiusMeters).isEqualTo(2000);   // 60초 경과 → 1km + 2단계
        assertThat(notifier.notifiedDrivers).containsExactly(1L);
    }

    @Test
    void 재탐색에서_이미_콜을_받은_기사에게는_다시_알림이_가지_않는다() {
        매칭중(방번호, Duration.ofSeconds(30));
        candidates.add(방번호, List.of(1L));   // 첫 탐색에서 이미 호출됨
        locations.nearby = List.of(1L, 2L);

        sweeper.sweep();

        assertThat(notifier.notifiedDrivers).containsExactly(2L);
    }

    @Test
    void 첫_탐색이_유실된_방도_스위퍼가_대신_탐색을_시작한다() {
        // 매칭 시작 커밋 직후 서버가 죽어 dispatch 리스너가 못 돈 경우 - 방이 버려지면 안 된다
        매칭중(방번호, Duration.ofSeconds(10));
        locations.nearby = List.of(1L);

        sweeper.sweep();

        assertThat(notifier.notifiedDrivers).containsExactly(1L);   // 스위퍼가 첫 콜을 대신 발송
    }

    @Test
    void 타임아웃이면_방을_해산하고_콜을_정리한다() {
        매칭중(방번호, Duration.ofMinutes(3));
        candidates.add(방번호, List.of(1L, 2L));

        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
        assertThat(candidates.findAll(방번호)).isEmpty();               // 명단이 남으면 늦은 수락이 통과된다
        assertThat(notifier.closedDrivers).containsExactlyInAnyOrder(1L, 2L);   // 대기 중이던 기사들 콜 카드 닫기
        assertThat(notifier.callCount).isZero();                        // 해산하는 방에 새 콜을 뿌리면 안 됨
    }

    @Test
    void 정확히_3분이_된_방도_해산된다() {
        // 경계값 - 판정이 초과(>)로 잘못 구현되면 스윕 주기만큼 수명이 늘어난다
        매칭중(방번호, DispatchService.MATCHING_TIMEOUT);

        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
    }

    @Test
    void 시작_시각이_없는_방은_즉시_해산한다() {
        // 컬럼 도입 이전에 MATCHING이 된 옛 데이터 - 영원히 갇히면 안 된다
        partyAccess.matchingTargets.add(new MatchingTarget(방번호, null));

        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
    }

    @Test
    void 스윕_도중_수락된_방은_해산되지_않는다() {
        // 경합 재현: 스윕이 방 목록을 읽은 직후 수락이 완료된 상황
        // giveUp의 첫 줄 failMatching이 "이미 배정됨"으로 거부되어야 뒷정리(마감통지)가 오발사되지 않는다
        매칭중(방번호, Duration.ofMinutes(3));
        partyAccess.assignedDriverId = 7L;   // 수락 완료
        candidates.add(방번호, List.of(9L));  // 아직 정리 전 잔여 후보가 있다고 가정

        assertThatCode(sweeper::sweep).doesNotThrowAnyException();

        assertThat(partyAccess.matchingCanceled).isEmpty();       // 배정된 방을 해산하면 안 됨
        assertThat(notifier.closedDrivers).isEmpty();             // 마감 통지 오발사 금지
        assertThat(candidates.findAll(방번호)).containsExactly(9L); // 정리는 수락 흐름의 몫
    }

    @Test
    void 요약이_없는_방이_있어도_다른_방은_계속_스윕한다() {
        // 방이 삭제되는 등 findSummary가 비는 경합 - attempt의 예외가 격리되어야 한다
        partyAccess.matchingTargets.add(new MatchingTarget(999L, Instant.now().minusSeconds(30)));
        매칭중(방번호, Duration.ofMinutes(3));

        assertThatCode(sweeper::sweep).doesNotThrowAnyException();

        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
    }

    @Test
    void 한_방의_처리가_실패해도_다른_방은_계속_스윕한다() {
        // 1번 방은 스윕과 수락이 겹쳐 failMatching 이 예외를 던지는 상황
        매칭중(방번호, Duration.ofMinutes(3));
        매칭중(다른방번호, Duration.ofMinutes(3));
        partyAccess.cancelRejected.add(방번호);

        assertThatCode(sweeper::sweep).doesNotThrowAnyException();   // 예외가 스케줄러로 새면 안 됨

        assertThat(partyAccess.matchingCanceled).containsExactly(다른방번호);
    }

    @Test
    void 후보가_없는_방이_타임아웃되면_마감통지_없이_조용히_해산한다() {
        // 3분 내내 반경에 기사가 한 명도 안 잡힌 방
        매칭중(방번호, Duration.ofMinutes(3));

        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
        assertThat(notifier.closedDrivers).isEmpty();   // 빈 명단에 통지를 시도해도 안전해야 함
    }

    @Test
    void 매칭중인_방이_없으면_아무_일도_하지_않는다() {
        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).isEmpty();
        assertThat(notifier.callCount).isZero();
    }
}
