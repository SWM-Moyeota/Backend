package team.codingforest.moyeota.dispatch.domain;

import java.util.List;

public interface CallCandidates {
    void register(Long partyId, List<Long> driverIds);
    boolean contains(Long partyId, Long driverId);
    long remove(Long partyId, Long driverId);
    List<Long> findAll(Long partyId);
    void clear(Long partyId);
}
