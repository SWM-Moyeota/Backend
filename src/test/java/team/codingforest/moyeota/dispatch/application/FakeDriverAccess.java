package team.codingforest.moyeota.dispatch.application;

import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.driver.api.DriverSummary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 *  콜 가능 여부만 제어하는 페이크 - FCM 토큰은 없는 것으로 취급
 */
class FakeDriverAccess implements DriverAccess {
    private final Set<Long> 콜가능기사들;
    final Map<Long, Long> 기사의유저 = new HashMap<>();   // driverId → userId (셀프 배차 시나리오용)

    FakeDriverAccess(Set<Long> 콜가능기사들) {
        this.콜가능기사들 = 콜가능기사들;
    }

    @Override
    public boolean canReceiveCalls(Long driverId) {
        return 콜가능기사들.contains(driverId);
    }

    @Override
    public Map<Long, String> findFcmTokens(List<Long> driverIds) {
        return Map.of();
    }

    @Override
    public Optional<Long> findUserId(Long driverId) {
        return Optional.ofNullable(기사의유저.get(driverId));
    }

    @Override
    public Optional<DriverSummary> findSummary(Long driverId) {
        return Optional.empty();
    }
}
