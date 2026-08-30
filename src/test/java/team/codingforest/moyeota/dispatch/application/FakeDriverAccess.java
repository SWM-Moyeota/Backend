package team.codingforest.moyeota.dispatch.application;

import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.driver.api.DriverSummary;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 *  콜 가능 여부만 제어하는 페이크 - FCM 토큰은 없는 것으로 취급
 */
class FakeDriverAccess implements DriverAccess {
    private final Set<Long> 콜가능기사들;

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
    public Optional<DriverSummary> findSummary(Long driverId) {
        return Optional.empty();
    }
}
