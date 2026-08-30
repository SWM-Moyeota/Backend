package team.codingforest.moyeota.dispatch.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.dispatch.domain.DriverLocations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class DriverLocationServiceTest {

    private static final Long 기사 = 1L;
    private static final double 강남역_위도 = 37.4979;
    private static final double 강남역_경도 = 127.0276;

    private RecordingDriverLocations locations;

    @BeforeEach
    void setUp() {
        locations = new RecordingDriverLocations();
    }

    private DriverLocationService serviceWith(Set<Long> 콜가능기사들) {
        return new DriverLocationService(locations, new FakeDriverAccess(콜가능기사들));
    }

    @Test
    void 콜_가능한_기사는_운행을_시작하면_위치가_등록된다() {
        DriverLocationService service = serviceWith(Set.of(기사));

        service.goOnline(기사, 강남역_위도, 강남역_경도);

        assertThat(locations.updatedDrivers).containsExactly(기사);
    }

    @Test
    void 콜_불가능한_기사는_운행을_시작할_수_없다() {
        DriverLocationService service = serviceWith(Set.of());

        assertThatThrownBy(() -> service.goOnline(기사, 강남역_위도, 강남역_경도))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(locations.updatedDrivers).isEmpty();
    }

    @Test
    void 위치_보고는_검증_없이_위치를_갱신한다() {
        DriverLocationService service = serviceWith(Set.of());   // 콜 불가 상태여도

        service.report(기사, 강남역_위도, 강남역_경도);

        assertThat(locations.updatedDrivers).containsExactly(기사);
    }

    @Test
    void 운행을_종료하면_위치가_제거된다() {
        DriverLocationService service = serviceWith(Set.of(기사));
        service.goOnline(기사, 강남역_위도, 강남역_경도);

        service.goOffline(기사);

        assertThat(locations.removedDrivers).containsExactly(기사);
    }

    /**
     *  update/remove 호출을 기록만 하는 가짜 위치 저장소
     */
    static class RecordingDriverLocations implements DriverLocations {
        @Override
        public java.util.Optional<team.codingforest.moyeota.dispatch.domain.DriverPosition> getLocationDriver(Long driverId) {
            return java.util.Optional.empty();
        }

        List<Long> updatedDrivers = new ArrayList<>();
        List<Long> removedDrivers = new ArrayList<>();

        @Override
        public void update(Long driverId, double latitude, double longitude) {
            updatedDrivers.add(driverId);
        }

        @Override
        public void remove(Long driverId) {
            removedDrivers.add(driverId);
        }

        @Override
        public List<Long> findNearby(double latitude, double longitude, int radiusMeters) {
            return List.of();
        }
    }
}