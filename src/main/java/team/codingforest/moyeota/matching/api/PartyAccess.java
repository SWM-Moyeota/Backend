package team.codingforest.moyeota.matching.api;

import java.util.Optional;

public interface PartyAccess {
    Optional<PartySummary> findSummary(Long partyId);
}
