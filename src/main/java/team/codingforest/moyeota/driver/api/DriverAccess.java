package team.codingforest.moyeota.driver.api;

import java.util.List;
import java.util.Map;

public interface DriverAccess {
    boolean canReceiveCalls(Long driverId);
    Map<Long, String> findFcmTokens(List<Long> driverIds);
}
