package team.codingforest.moyeota.matching.domain;

public interface RouteFinder {
    RouteEstimate find(RouteKey key);
}
