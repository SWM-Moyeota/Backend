package team.codingforest.moyeota.matching.party;

import team.codingforest.moyeota.matching.route.domain.RouteCache;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;
import team.codingforest.moyeota.matching.route.domain.RouteKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 인메모리 가짜 RouteCache (PartyJpaTest 와 같은 역할) */
class RouteCacheTest implements RouteCache {
    private final Map<String, RouteEstimate> store = new HashMap<>();

    @Override
    public Optional<RouteEstimate> find(RouteKey key) {
        return Optional.ofNullable(store.get(key.value()));
    }

    @Override
    public void save(RouteKey key, RouteEstimate estimate) {
        store.put(key.value(), estimate);
    }
}
