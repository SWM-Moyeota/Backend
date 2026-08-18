package team.codingforest.moyeota.dispatch.domain;

import java.util.List;

public interface DriverLocations {
    void update(Long driverId, double latitude, double longitude);
    void remove(Long driverId);
    List<Long> findNearby(double latitude, double longitude, int radiusMeters);
}
