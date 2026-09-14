package team.codingforest.moyeota.chat.domain;

import java.util.Map;

public interface PartyLocations {
    void startSession(Long userId, Long partyId);
    void put(Long userId, Long partyId, PartyLocation partyLocation);
    Map<Long, PartyLocation> findAll(Long partyId);
    boolean isSharing(Long userId, Long partyId);
    void stop(Long userId, Long partyId);
}
