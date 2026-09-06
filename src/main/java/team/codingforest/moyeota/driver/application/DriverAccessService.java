package team.codingforest.moyeota.driver.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.driver.api.DriverInfo;
import team.codingforest.moyeota.driver.api.DriverSummary;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class DriverAccessService implements DriverAccess {

    private final Drivers drivers;

    @Override
    public boolean canReceiveCalls(Long driverId) {
        return drivers.findById(driverId)
                .map(Driver::canReceiveCalls)
                .orElse(false);
    }

    @Override
    public Map<Long, String> findFcmTokens(List<Long> driverIds) {
        Map<Long, String> tokens = new HashMap<>();

        for(Long driverId : driverIds) {
            drivers.findById(driverId)
                    .filter(Driver::hasFcmToken)
                    .ifPresent(d -> tokens.put(driverId, d.getFcmToken()));
        }
        return tokens;
    }

    @Override
    public Optional<Long> findUserId(Long driverId) {
        return drivers.findById(driverId).map(Driver::getUserId);
    }

    @Override
    public Optional<DriverSummary> findSummary(Long driverId) {
        return drivers.findById(driverId)
                .filter(d -> d.getVehicle() != null)
                .map(d -> new DriverSummary(d.getVehicle().seats(), d.getVehicle().plateNumber(), d.getVehicle().type()));
    }
}
