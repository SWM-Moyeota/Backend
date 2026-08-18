package team.codingforest.moyeota.dispatch.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.dispatch.domain.CallNotifier;
import team.codingforest.moyeota.dispatch.domain.DriverLocations;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class DispatchServiceTest {

    private static final Long 방번호 = 1L;
    private static final PartySummary 강남출발방 =
            new PartySummary(방번호, 37.4979, 127.0276, "강남역", "판교역");

    private FakeDriverLocations locations;
    private RecordingNotifier notifier;

    @BeforeEach
    void setUp() {
        locations = new FakeDriverLocations();
        notifier = new RecordingNotifier();
    }

    private DispatchService serviceWith(PartyAccess partyAccess, Set<Long> 콜가능기사들) {
        return new DispatchService(partyAccess, 콜가능기사들::contains, locations, notifier);
    }

    @Test
    void 반경_내_콜_가능한_기사에게만_알림을_보낸다() {
        locations.nearby = List.of(1L, 2L, 3L);

        DispatchService service = serviceWith(id -> Optional.of(강남출발방), Set.of(1L, 3L));
        service.dispatch(방번호);

        assertThat(notifier.notifiedDrivers).containsExactly(1L, 3L);
    }

    @Test
    void 콜_가능한_기사가_없으면_알림을_보내지_않는다() {
        locations.nearby = List.of(1L, 2L);

        DispatchService service = serviceWith(id -> Optional.of(강남출발방), Set.of());
        service.dispatch(방번호);

        assertThat(notifier.callCount).isZero();
    }

    @Test
    void 반경_내_기사가_없으면_알림을_보내지_않는다() {
        locations.nearby = List.of();

        DispatchService service = serviceWith(id -> Optional.of(강남출발방), Set.of(1L));
        service.dispatch(방번호);

        assertThat(notifier.callCount).isZero();
    }

    @Test
    void 없는_방이면_예외가_발생한다() {
        DispatchService service = serviceWith(id -> Optional.empty(), Set.of(1L));

        assertThatThrownBy(() -> service.dispatch(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     *  누구에게 몇 번 알림이 갔는지 기록만 하는 가짜
     */
    static class RecordingNotifier implements CallNotifier {
        List<Long> notifiedDrivers = new ArrayList<>();
        int callCount = 0;

        @Override
        public void notifyCall(List<Long> driverIds, PartySummary party) {
            callCount++;
            notifiedDrivers.addAll(driverIds);
        }
    }

    /**
     *  정해준 목록을 그대로 돌려주는 가짜 위치 저장소
     */
    static class FakeDriverLocations implements DriverLocations {
        List<Long> nearby = new ArrayList<>();

        @Override
        public void update(Long driverId, double latitude, double longitude) {}

        @Override
        public void remove(Long driverId) {}

        @Override
        public List<Long> findNearby(double latitude, double longitude, int radiusMeters) {
            return nearby;
        }
    }
}