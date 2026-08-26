package team.codingforest.moyeota.dispatch.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.dispatch.domain.CallCandidates;
import team.codingforest.moyeota.dispatch.domain.CallNotifier;
import team.codingforest.moyeota.dispatch.domain.DriverLocations;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class DispatchServiceTest {

    private static final Long 방번호 = 1L;
    private static final PartySummary 강남출발방 =
            new PartySummary(방번호, 37.4979, 127.0276, "강남역", "판교역");

    private FakeDriverLocations locations;
    private RecordingNotifier notifier;
    private InMemoryCallCandidates candidates;
    private FakePartyAccess partyAccess;

    @BeforeEach
    void setUp() {
        locations = new FakeDriverLocations();
        notifier = new RecordingNotifier();
        candidates = new InMemoryCallCandidates();
        partyAccess = new FakePartyAccess(강남출발방);
    }

    private DispatchService serviceWith(Set<Long> 콜가능기사들) {
        return new DispatchService(partyAccess, new FakeDriverAccess(콜가능기사들), locations, notifier, candidates);
    }

    // ───────────────────────── dispatch ─────────────────────────

    @Test
    void 반경_내_콜_가능한_기사에게만_알림을_보내고_후보로_기록한다() {
        locations.nearby = List.of(1L, 2L, 3L);
        DispatchService service = serviceWith(Set.of(1L, 3L));

        service.dispatch(방번호);

        assertThat(notifier.notifiedDrivers).containsExactly(1L, 3L);
        assertThat(candidates.findAll(방번호)).containsExactlyInAnyOrder(1L, 3L);   // 기록이 없으면 수락이 불가능
    }

    @Test
    void 콜_알림은_한_번만_발송된다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));

        service.dispatch(방번호);

        assertThat(notifier.callCount).isEqualTo(1);   // 중복 발송이면 기사에게 콜 카드가 두 번 뜬다
    }

    @Test
    void 콜_가능한_기사가_없으면_알림_없이_매칭을_되돌린다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of());

        service.dispatch(방번호);

        assertThat(notifier.callCount).isZero();
        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);   // 방이 MATCHING 에 갇히면 안 됨
    }

    @Test
    void 반경_내_기사가_없으면_알림_없이_매칭을_되돌린다() {
        locations.nearby = List.of();
        DispatchService service = serviceWith(Set.of(1L));

        service.dispatch(방번호);

        assertThat(notifier.callCount).isZero();
        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
    }

    @Test
    void 없는_방이면_예외가_발생한다() {
        DispatchService service = serviceWith(Set.of(1L));

        assertThatThrownBy(() -> service.dispatch(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────────────────── acceptCall ─────────────────────────

    @Test
    void 수락하면_기사가_배정되고_콜이_마감된다() {
        locations.nearby = List.of(1L, 2L, 3L);
        DispatchService service = serviceWith(Set.of(1L, 2L, 3L));
        service.dispatch(방번호);

        service.acceptCall(방번호, 2L);

        assertThat(partyAccess.assignedDriverId).isEqualTo(2L);
        assertThat(service.isCallOpen(방번호, 1L)).isFalse();   // 마감 — 폴링하던 앱이 카드를 닫는 근거
        assertThat(service.isCallOpen(방번호, 3L)).isFalse();
    }

    @Test
    void 수락한_기사는_기사_검색_풀에서_제외된다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);

        service.acceptCall(방번호, 1L);

        assertThat(locations.removed).containsExactly(1L);   // 제외 안 하면 배차된 기사가 다른 콜을 계속 받는다
    }

    @Test
    void 수락하면_나머지_후보에게만_마감_통지가_간다() {
        locations.nearby = List.of(1L, 2L, 3L);
        DispatchService service = serviceWith(Set.of(1L, 2L, 3L));
        service.dispatch(방번호);

        service.acceptCall(방번호, 2L);

        assertThat(notifier.closedDrivers).containsExactlyInAnyOrder(1L, 3L);   // 수락자 본인은 제외
    }

    @Test
    void 호출받지_않은_기사는_수락할_수_없다() {
        locations.nearby = List.of(1L);
        DispatchService service = serviceWith(Set.of(1L, 99L));
        service.dispatch(방번호);

        // 99번은 콜 가능하지만 반경 밖이라 호출 안 됨 — URL 조작 시나리오
        assertThatThrownBy(() -> service.acceptCall(방번호, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("호출받지 않은 콜입니다.");
        assertThat(partyAccess.assignedDriverId).isNull();
    }

    @Test
    void 이미_마감된_콜은_늦게_수락해도_배정되지_않는다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);
        service.acceptCall(방번호, 1L);

        // 기사 2가 마감 통지를 못 받고 수락 버튼을 누른 경우
        assertThatThrownBy(() -> service.acceptCall(방번호, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("호출받지 않은 콜입니다.");
        assertThat(partyAccess.assignedDriverId).isEqualTo(1L);   // 배정은 그대로 1번
    }

    @Test
    void 콜을_받은_후_오프라인이_된_기사는_수락할_수_없다() {
        locations.nearby = List.of(1L, 2L);
        Set<Long> 콜가능 = new HashSet<>(Set.of(1L, 2L));
        DispatchService service = new DispatchService(partyAccess, new FakeDriverAccess(콜가능), locations, notifier, candidates);
        service.dispatch(방번호);

        콜가능.remove(1L);   // 콜을 받은 뒤 콜 OFF / 오프라인 전환

        assertThatThrownBy(() -> service.acceptCall(방번호, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("콜을 받을 수 없는 기사입니다.");
        assertThat(service.isCallOpen(방번호, 2L)).isTrue();   // 콜 자체는 살아 있어야 함
    }

    @Test
    void 동시_수락_경합에서_늦은_기사는_배정_예외를_받고_배정은_유지된다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);

        // 경합 재현: 1번 배정 확정 직후, 마감(clear) 전에 2번의 요청이 assignDriver 까지 도달한 상황
        partyAccess.assignDriver(방번호, 1L);

        assertThatThrownBy(() -> service.acceptCall(방번호, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 기사가 배정된 방입니다.");
        assertThat(partyAccess.assignedDriverId).isEqualTo(1L);
    }

    // ───────────────────────── rejectCall ─────────────────────────

    @Test
    void 거절하면_본인만_후보에서_빠지고_콜은_유지된다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);

        service.rejectCall(방번호, 1L);

        assertThat(service.isCallOpen(방번호, 1L)).isFalse();
        assertThat(service.isCallOpen(방번호, 2L)).isTrue();
        assertThat(partyAccess.matchingCanceled).isEmpty();   // 아직 후보가 남아 있으니 되돌리면 안 됨
    }

    @Test
    void 전원이_거절하면_매칭을_되돌린다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);

        service.rejectCall(방번호, 1L);
        service.rejectCall(방번호, 2L);

        assertThat(partyAccess.matchingCanceled).containsExactly(방번호);
    }

    @Test
    void 같은_기사가_두_번_거절하면_두번째는_예외가_발생한다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);
        service.rejectCall(방번호, 1L);

        // 이중 탭 — 이미 명단에서 빠졌으므로 남은 후보 수를 또 줄이면 안 된다
        assertThatThrownBy(() -> service.rejectCall(방번호, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("호출받지 않은 콜입니다.");
        assertThat(partyAccess.matchingCanceled).isEmpty();   // 2번이 남아 있는데 되돌아가면 안 됨
    }

    @Test
    void 마감된_콜을_거절해도_매칭이_되돌아가지_않는다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);
        service.acceptCall(방번호, 1L);

        // 기사 2가 마감 통지 전에 거절 버튼을 누른 경우
        assertThatThrownBy(() -> service.rejectCall(방번호, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("호출받지 않은 콜입니다.");
        assertThat(partyAccess.matchingCanceled).isEmpty();   // 이미 배정된 방을 되돌리려 하면 안 됨
    }

    @Test
    void 호출받지_않은_기사는_거절할_수_없다() {
        locations.nearby = List.of(1L);
        DispatchService service = serviceWith(Set.of(1L));
        service.dispatch(방번호);

        assertThatThrownBy(() -> service.rejectCall(방번호, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("호출받지 않은 콜입니다.");
    }

    // ───────────────────────── isCallOpen ─────────────────────────

    @Test
    void 호출받지_않은_기사의_상태_조회는_닫힘이다() {
        locations.nearby = List.of(1L);
        DispatchService service = serviceWith(Set.of(1L));
        service.dispatch(방번호);

        assertThat(service.isCallOpen(방번호, 99L)).isFalse();
        assertThat(service.isCallOpen(999L, 1L)).isFalse();   // 없는 방도 예외가 아니라 닫힘
    }

    // ───────────────────────── 가짜들 ─────────────────────────

    /**
     *  방 요약을 돌려주고 배정/되돌림을 기록하는 가짜.
     *  assignDriver 는 실제 도메인 가드(중복 배정 불가)를 흉내낸다.
     */
    static class FakePartyAccess implements PartyAccess {
        private final PartySummary summary;
        Long assignedDriverId;
        List<Long> matchingCanceled = new ArrayList<>();
        List<Long> staleIds = new ArrayList<>();

        FakePartyAccess(PartySummary summary) {
            this.summary = summary;
        }

        @Override
        public Optional<PartySummary> findSummary(Long partyId) {
            return summary != null && summary.id().equals(partyId) ? Optional.of(summary) : Optional.empty();
        }

        @Override
        public void assignDriver(Long partyId, Long driverId) {
            if(assignedDriverId != null) throw new IllegalArgumentException("이미 기사가 배정된 방입니다.");
            assignedDriverId = driverId;
        }

        @Override
        public void cancelMatching(Long partyId) {
            if(assignedDriverId != null) throw new IllegalArgumentException("이미 기사가 배정된 방입니다.");
            matchingCanceled.add(partyId);
        }

        @Override
        public List<Long> findStaleMatchingIds(java.time.Instant cutoff) {
            return List.copyOf(staleIds);
        }
    }

    /**
     *  인메모리 후보 명단 (CallCandidatesRedis 대응)
     */
    static class InMemoryCallCandidates implements CallCandidates {
        private final Map<Long, Set<Long>> store = new HashMap<>();

        @Override
        public void register(Long partyId, List<Long> driverIds) {
            store.put(partyId, new HashSet<>(driverIds));
        }

        @Override
        public boolean contains(Long partyId, Long driverId) {
            return store.getOrDefault(partyId, Set.of()).contains(driverId);
        }

        @Override
        public long remove(Long partyId, Long driverId) {
            Set<Long> set = store.getOrDefault(partyId, new HashSet<>());
            set.remove(driverId);
            return set.size();
        }

        @Override
        public List<Long> findAll(Long partyId) {
            return List.copyOf(store.getOrDefault(partyId, Set.of()));
        }

        @Override
        public void clear(Long partyId) {
            store.remove(partyId);
        }
    }

    /**
     *  누구에게 몇 번 알림/마감이 갔는지 기록만 하는 가짜
     */
    static class RecordingNotifier implements CallNotifier {
        List<Long> notifiedDrivers = new ArrayList<>();
        List<Long> closedDrivers = new ArrayList<>();
        int callCount = 0;

        @Override
        public void notifyCall(List<Long> driverIds, PartySummary party) {
            callCount++;
            notifiedDrivers.addAll(driverIds);
        }

        @Override
        public void notifyCallClosed(List<Long> driverIds, Long partyId) {
            closedDrivers.addAll(driverIds);
        }
    }

    /**
     *  정해준 목록을 그대로 돌려주고 제거를 기록하는 가짜 위치 저장소
     */
    static class FakeDriverLocations implements DriverLocations {
        List<Long> nearby = new ArrayList<>();
        List<Long> removed = new ArrayList<>();

        @Override
        public void update(Long driverId, double latitude, double longitude) {}

        @Override
        public void remove(Long driverId) {
            removed.add(driverId);
        }

        @Override
        public List<Long> findNearby(double latitude, double longitude, int radiusMeters) {
            return nearby;
        }
    }
}
