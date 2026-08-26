package team.codingforest.moyeota.matching.domain;

import java.util.Optional;

public interface RouteCache {
    Optional<RouteEstimate> find(RouteKey key);
    void save(RouteKey key, RouteEstimate estimate);
}
