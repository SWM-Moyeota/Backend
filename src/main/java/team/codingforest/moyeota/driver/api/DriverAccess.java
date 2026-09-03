package team.codingforest.moyeota.driver.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DriverAccess {
    boolean canReceiveCalls(Long driverId);
    Map<Long, String> findFcmTokens(List<Long> driverIds);
    Optional<DriverSummary> findSummary(Long driverId);

    /** 셀프 배차 차단용 - 기사가 어느 유저인지 */
    Optional<Long> findUserId(Long driverId);
}
