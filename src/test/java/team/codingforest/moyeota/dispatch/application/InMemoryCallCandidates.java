package team.codingforest.moyeota.dispatch.application;

import team.codingforest.moyeota.dispatch.domain.CallCandidates;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *  인메모리 후보 명단 (CallCandidatesRedis 대응 - add 는 누적)
 */
class InMemoryCallCandidates implements CallCandidates {
    private final Map<Long, Set<Long>> store = new HashMap<>();

    @Override
    public void add(Long partyId, List<Long> driverIds) {
        store.computeIfAbsent(partyId, k -> new HashSet<>()).addAll(driverIds);
    }

    @Override
    public boolean contains(Long partyId, Long driverId) {
        return store.getOrDefault(partyId, Set.of()).contains(driverId);
    }

    @Override
    public long remove(Long partyId, Long driverId) {
        Set<Long> set = store.getOrDefault(partyId, new HashSet<>());
        set.remove(driverId);
        return set.size();
    }

    @Override
    public List<Long> findAll(Long partyId) {
        return List.copyOf(store.getOrDefault(partyId, Set.of()));
    }

    @Override
    public void clear(Long partyId) {
        store.remove(partyId);
        rejected.remove(partyId);
    }

    private final Map<Long, Set<Long>> rejected = new HashMap<>();

    @Override
    public void markRejected(Long partyId, Long driverId) {
        rejected.computeIfAbsent(partyId, k -> new HashSet<>()).add(driverId);
    }

    @Override
    public List<Long> findRejected(Long partyId) {
        return List.copyOf(rejected.getOrDefault(partyId, Set.of()));
    }
}
