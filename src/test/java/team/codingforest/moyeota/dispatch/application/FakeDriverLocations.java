package team.codingforest.moyeota.dispatch.application;

import team.codingforest.moyeota.dispatch.domain.DriverLocations;
import team.codingforest.moyeota.dispatch.domain.DriverPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 *  정해준 목록을 그대로 돌려주고, 마지막 탐색 반경과 제거를 기록하는 가짜 위치 저장소
 */
class FakeDriverLocations implements DriverLocations {
    List<Long> nearby = new ArrayList<>();
    final List<Long> removed = new ArrayList<>();
    int lastRadiusMeters = -1;

    @Override
    public void update(Long driverId, double latitude, double longitude) {}

    @Override
    public void remove(Long driverId) {
        removed.add(driverId);
    }

    @Override
    public List<Long> findNearby(double latitude, double longitude, int radiusMeters) {
        lastRadiusMeters = radiusMeters;
        return nearby;
    }

    DriverPosition position;   // null이면 위치 신호 없음(heartbeat 만료)으로 취급

    @Override
    public Optional<DriverPosition> getLocationDriver(Long driverId) {
        return Optional.ofNullable(position);
    }
}
