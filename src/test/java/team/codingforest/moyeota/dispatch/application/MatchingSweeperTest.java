package team.codingforest.moyeota.dispatch.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class MatchingSweeperTest {

    private static final Long 방번호 = 1L;
    private static final Long 다른방번호 = 2L;
    private static final PartySummary 강남출발방 =
            new PartySummary(방번호, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 2, 12000, 25);
    private static final PartySummary 판교출발방 =
            new PartySummary(다른방번호, 37.3948, 127.1112, 37.4979, 127.0276, "판교역", "강남역", 3, 15000, 30);

    private FakeDriverLocations locations;
    private RecordingNotifier notifier;
    private InMemoryCallCandidates candidates;
    private FakePartyAccess partyAccess;
    private FakeMatchingTimers timers;
    private MatchingSweeper sweeper;

    @BeforeEach
    void setUp() {
        locations = new FakeDriverLocations();
        notifier = new RecordingNotifier();
        candidates = new InMemoryCallCandidates();
        partyAccess = new FakePartyAccess(강남출발방, 판교출발방);
        timers = new FakeMatchingTimers();

        DispatchService dispatchService = new DispatchService(timers, partyAccess, new FakeDriverAccess(Set.of(1L, 2L, 3L)), locations, notifier, candidates);
        sweeper = new MatchingSweeper(partyAccess, dispatchService, timers, candidates, notifier);
    }

    @Test
    void 타임아웃_전이면_경과_시간에_맞는_반경으로_재탐색한다() {
        partyAccess.matchingIds = List.of(방번호);
        timers.elapsedByParty.put(방번호, Duration.ofSeconds(60));
        locations.nearby = List.of(1L);

        sweeper.sweep();

        assertThat(locations.lastRadiusMeters).isEqualTo(2000);   // 60초 경과 → 1km + 2단계
        assertThat(notifier.notifiedDrivers).containsExactly(1L);
    }

    @Test
    void 재탐색에서_이미_콜을_받은_기사에게는_다시_알림이_가지_않는다() {
        partyAccess.matchingIds = List.of(방번호);
        timers.elapsedByParty.put(방번호, Duration.ofSeconds(30));
        candidates.add(방번호, List.of(1L));   // 첫 탐색에서 이미 호출됨
        locations.nearby = List.of(1L, 2L);

        sweeper.sweep();

        assertThat(notifier.notifiedDrivers).containsExactly(2L);
    }

    @Test
    void 타임아웃이면_매칭을_되돌리고_콜을_정리한다() {
        partyAccess.matchingIds = List.of(방번호);
        timers.elapsedByParty.put(방번호, Duration.ofMinutes(3));
        candidates.add(방번호, List.of(1L, 2L));

        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
        assertThat(candidates.findAll(방번호)).isEmpty();               // 명단이 남으면 늦은 수락이 통과된다
        assertThat(timers.cleared).containsExactly(방번호);
        assertThat(notifier.closedDrivers).containsExactlyInAnyOrder(1L, 2L);   // 대기 중이던 기사들 콜 카드 닫기
        assertThat(notifier.callCount).isZero();                        // 접는 방에 새 콜을 뿌리면 안 됨
    }

    @Test
    void 타이머가_유실된_방은_즉시_타임아웃_처리한다() {
        // 서버 재시작 등으로 Redis 타이머만 사라진 경우 - 영원히 MATCHING에 갇히면 안 된다
        partyAccess.matchingIds = List.of(방번호);

        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
    }

    @Test
    void 한_방의_처리가_실패해도_다른_방은_계속_스윕한다() {
        // 1번 방은 스윕과 수락이 겹쳐 cancelMatching 이 예외를 던지는 상황
        partyAccess.matchingIds = List.of(방번호, 다른방번호);
        partyAccess.cancelRejected.add(방번호);
        timers.elapsedByParty.put(방번호, Duration.ofMinutes(3));
        timers.elapsedByParty.put(다른방번호, Duration.ofMinutes(3));

        assertThatCode(sweeper::sweep).doesNotThrowAnyException();   // 예외가 스케줄러로 새면 안 됨

        assertThat(partyAccess.matchingCanceled).containsExactly(다른방번호);
    }

    @Test
    void 매칭중인_방이_없으면_아무_일도_하지_않는다() {
        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).isEmpty();
        assertThat(notifier.callCount).isZero();
    }

    // ───────────────────────── 경합/예외 상황 ─────────────────────────

    @Test
    void 스윕_도중_수락된_방은_타임아웃_처리하지_않는다() {
        // 경합 재현: 스윕이 방 목록을 읽은 직후 수락이 완료된 상황
        // - 수락이 타이머를 지웠으므로 elapsed는 "유실"로 보이고, 스위퍼는 giveUp을 시도한다
        // - giveUp의 첫 줄 cancelMatching이 "이미 배정됨"으로 거부되어야 뒷정리(마감통지)가 오발사되지 않는다
        partyAccess.matchingIds = List.of(방번호);
        partyAccess.assignedDriverId = 7L;   // 수락 완료
        candidates.add(방번호, List.of(9L));  // 아직 정리 전 잔여 후보가 있다고 가정

        assertThatCode(sweeper::sweep).doesNotThrowAnyException();

        assertThat(partyAccess.matchingCanceled).isEmpty();       // 배정된 방을 되돌리면 안 됨
        assertThat(notifier.closedDrivers).isEmpty();             // 마감 통지 오발사 금지
        assertThat(candidates.findAll(방번호)).containsExactly(9L); // 정리는 수락 흐름의 몫
    }

    @Test
    void 요약이_없는_방이_있어도_다른_방은_계속_스윕한다() {
        // 방이 삭제되는 등 findSummary가 비는 경합 - attempt의 예외가 격리되어야 한다
        partyAccess.matchingIds = List.of(999L, 방번호);
        timers.elapsedByParty.put(999L, Duration.ofSeconds(30));
        timers.elapsedByParty.put(방번호, Duration.ofMinutes(3));

        assertThatCode(sweeper::sweep).doesNotThrowAnyException();

        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
    }

    @Test
    void 후보가_없는_방이_타임아웃되면_마감통지_없이_조용히_접는다() {
        // 3분 내내 반경에 기사가 한 명도 안 잡힌 방
        partyAccess.matchingIds = List.of(방번호);
        timers.elapsedByParty.put(방번호, Duration.ofMinutes(3));

        sweeper.sweep();

        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
        assertThat(notifier.closedDrivers).isEmpty();   // 빈 명단에 통지를 시도해도 안전해야 함
    }
}
