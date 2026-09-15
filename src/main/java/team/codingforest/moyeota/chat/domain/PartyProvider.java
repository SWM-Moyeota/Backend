package team.codingforest.moyeota.chat.domain;

import java.util.Optional;

public interface PartyProvider {
    Optional<PartySnapshot> findSnapshot(Long partyId);
    boolean isActiveMember(Long userId, Long partyId);
}
