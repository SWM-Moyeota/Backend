package team.codingforest.moyeota.driver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.driver.api.DriverSummary;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;

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
        return drivers.findFcmTokens(driverIds);
    }

    @Override
    public Optional<Long> findUserId(Long driverId) {
        return drivers.findById(driverId).map(Driver::getUserId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> lockIdByUserId(Long userId) {
        return drivers.findByUserIdForUpdate(userId).map(Driver::getId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockAndCanReceiveCalls(Long driverId) {
        return drivers.findByIdForUpdate(driverId)
                .map(Driver::canReceiveCalls)
                .orElse(false);
    }

    @Override
    public Optional<DriverSummary> findSummary(Long driverId) {
        return drivers.findById(driverId)
                .filter(d -> d.getVehicle() != null)
                .map(d -> new DriverSummary(d.getVehicle().seats(), d.getVehicle().plateNumber(), d.getVehicle().type()));
    }
}
