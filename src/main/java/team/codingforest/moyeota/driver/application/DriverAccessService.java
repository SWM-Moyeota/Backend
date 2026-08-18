package team.codingforest.moyeota.driver.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;

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
}
