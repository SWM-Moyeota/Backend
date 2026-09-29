package team.codingforest.moyeota.matching.route.domain;

public interface RouteFinder {
    RouteEstimate find(RouteKey key);
}
