package team.codingforest.moyeota.dispatch.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.domain.exception.DispatchErrorCode;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class DispatchServiceTest {

    private static final Long 방번호 = 1L;
    private static final PartySummary 강남출발방 =
            new PartySummary(방번호, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 2, 12000, 25, null);

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

    // ───────────────────────── dispatch (첫 탐색) ─────────────────────────

    @Test
    void 반경_내_콜_가능한_기사에게만_알림을_보내고_후보로_기록한다() {
        locations.nearby = List.of(1L, 2L, 3L);
        DispatchService service = serviceWith(Set.of(1L, 3L));

        service.dispatch(방번호);

        assertThat(notifier.notifiedDrivers).containsExactly(1L, 3L);
        assertThat(candidates.findAll(방번호)).containsExactlyInAnyOrder(1L, 3L);   // 기록이 없으면 수락이 불가능
    }

    @Test
    void 첫_탐색은_초기_반경으로_수행된다() {
        locations.nearby = List.of(1L);
        DispatchService service = serviceWith(Set.of(1L));

        service.dispatch(방번호);

        assertThat(locations.lastRadiusMeters).isEqualTo(1000);   // 처음부터 3km를 뒤지면 가까운 기사 우선이 안 된다
    }

    @Test
    void 콜_가능한_기사가_없어도_매칭을_되돌리지_않는다() {
        // 새 정책: 포기 판정은 스위퍼의 타임아웃 하나로 일원화 - 반경을 넓히면 나올 수 있다
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of());

        service.dispatch(방번호);

        assertThat(notifier.callCount).isZero();
        assertThat(partyAccess.matchingCanceled).isEmpty();
    }

    @Test
    void 반경_내_기사가_없어도_매칭을_되돌리지_않는다() {
        locations.nearby = List.of();
        DispatchService service = serviceWith(Set.of(1L));

        service.dispatch(방번호);

        assertThat(notifier.callCount).isZero();
        assertThat(partyAccess.matchingCanceled).isEmpty();
    }

    @Test
    void 없는_방이면_예외가_발생한다() {
        DispatchService service = serviceWith(Set.of(1L));

        assertThatThrownBy(() -> service.dispatch(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DispatchErrorCode.PARTY_NOT_FOUND);
    }

    // ───────────────────────── attempt (재탐색) ─────────────────────────

    @Test
    void 재탐색은_새로_발견된_기사에게만_알림을_보낸다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L, 3L));
        service.dispatch(방번호);

        locations.nearby = List.of(1L, 2L, 3L);   // 반경이 넓어져 3번이 새로 잡힘
        service.attempt(방번호, 2000);

        assertThat(notifier.notifiedDrivers).containsExactly(1L, 2L, 3L);   // 1,2번에게 중복 콜이 가면 콜 카드가 두 번 뜬다
        assertThat(candidates.findAll(방번호)).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void 재탐색에서_신규_기사가_없으면_알림을_보내지_않는다() {
        locations.nearby = List.of(1L);
        DispatchService service = serviceWith(Set.of(1L));
        service.dispatch(방번호);

        service.attempt(방번호, 2000);

        assertThat(notifier.callCount).isEqualTo(1);   // 첫 탐색 1번뿐
    }

    @Test
    void 탐색_도중_새로_온라인이_된_기사도_다음_탐색에서_포함된다() {
        locations.nearby = List.of();
        DispatchService service = serviceWith(Set.of(1L));
        service.dispatch(방번호);

        locations.nearby = List.of(1L);   // 같은 반경이라도 새로 운행을 시작한 기사
        service.attempt(방번호, 1000);

        assertThat(notifier.notifiedDrivers).containsExactly(1L);
    }

    // ───────────────────────── radiusFor ─────────────────────────

    @Test
    void 반경은_30초마다_500m씩_넓어지고_3km에서_멈춘다() {
        DispatchService service = serviceWith(Set.of());

        assertThat(service.radiusFor(Duration.ofSeconds(0))).isEqualTo(1000);
        assertThat(service.radiusFor(Duration.ofSeconds(29))).isEqualTo(1000);
        assertThat(service.radiusFor(Duration.ofSeconds(30))).isEqualTo(1500);
        assertThat(service.radiusFor(Duration.ofSeconds(60))).isEqualTo(2000);
        assertThat(service.radiusFor(Duration.ofSeconds(120))).isEqualTo(3000);
        assertThat(service.radiusFor(Duration.ofMinutes(10))).isEqualTo(3000);   // 상한 고정
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
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DispatchErrorCode.CALL_CLOSED);
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
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DispatchErrorCode.CALL_CLOSED);
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
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DispatchErrorCode.DRIVER_CANNOT_RECEIVE);
        assertThat(service.isCallOpen(방번호, 2L)).isTrue();   // 콜 자체는 살아 있어야 함
    }

    @Test
    void 운행_중인_기사는_다른_방의_콜을_수락할_수_없다() {
        // 기사 이중 배정 차단: 이전 방을 운행 중인 기사가 (online 재호출 등으로) 새 콜을 받아 수락하는 경우
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);

        partyAccess.assignedDriverId = 1L;   // 기사 1은 이미 다른 방에 배정되어 운행 중

        assertThatThrownBy(() -> service.acceptCall(방번호, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DispatchErrorCode.DRIVER_ALREADY_RIDING);
        assertThat(service.isCallOpen(방번호, 2L)).isTrue();   // 콜 자체는 살아서 다른 기사가 수락 가능해야 함
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
        assertThat(partyAccess.matchingCanceled).isEmpty();
    }

    @Test
    void 전원이_거절해도_매칭을_되돌리지_않는다() {
        // 새 정책: 반경이 넓어지면 새 기사가 나올 수 있으므로 포기는 타임아웃 하나로 판정
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);

        service.rejectCall(방번호, 1L);
        service.rejectCall(방번호, 2L);

        assertThat(partyAccess.matchingCanceled).isEmpty();
    }

    @Test
    void 같은_기사가_두_번_거절하면_두번째는_예외가_발생한다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);
        service.rejectCall(방번호, 1L);

        // 이중 탭 — 이미 명단에서 빠졌으므로
        assertThatThrownBy(() -> service.rejectCall(방번호, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DispatchErrorCode.CALL_CLOSED);
    }

    @Test
    void 거절한_기사는_재탐색에서_다시_호출되지_않는다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L, 3L));
        service.dispatch(방번호);
        service.rejectCall(방번호, 1L);

        locations.nearby = List.of(1L, 2L, 3L);
        service.attempt(방번호, 2000);

        // 1번은 첫 콜 한 번만 - 거절한 콜이 다시 오면 안 된다
        assertThat(notifier.notifiedDrivers).containsExactly(1L, 2L, 3L);
        assertThat(service.isCallOpen(방번호, 1L)).isFalse();   // 재탐색 후에도 수락 자격은 없어야 함
    }

    @Test
    void 마감된_콜을_거절해도_예외만_나고_배정은_유지된다() {
        locations.nearby = List.of(1L, 2L);
        DispatchService service = serviceWith(Set.of(1L, 2L));
        service.dispatch(방번호);
        service.acceptCall(방번호, 1L);

        // 기사 2가 마감 통지 전에 거절 버튼을 누른 경우
        assertThatThrownBy(() -> service.rejectCall(방번호, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DispatchErrorCode.CALL_CLOSED);
        assertThat(partyAccess.assignedDriverId).isEqualTo(1L);
    }

    @Test
    void 호출받지_않은_기사는_거절할_수_없다() {
        locations.nearby = List.of(1L);
        DispatchService service = serviceWith(Set.of(1L));
        service.dispatch(방번호);

        assertThatThrownBy(() -> service.rejectCall(방번호, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DispatchErrorCode.CALL_CLOSED);
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
}
