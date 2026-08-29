package team.codingforest.moyeota.dispatch.domain;

import java.util.List;
import java.util.Optional;

public interface DriverLocations {
    void update(Long driverId, double latitude, double longitude);
    void remove(Long driverId);
    List<Long> findNearby(double latitude, double longitude, int radiusMeters);
    Optional<DriverPosition> getLocationDriver(Long driverId);
}
