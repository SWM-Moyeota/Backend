package team.codingforest.moyeota.matching.domain;

import team.codingforest.moyeota.matching.api.MatchingTarget;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface Parties {
    Optional<Party> findById(Long id);
    Party save(Party party);
    List<Party> findAllByStatus(PartyStatus status);
    boolean existsOngoingByMemberId(Long memberId);
    Optional<Party> findByIdForUpdate(Long id);
    List<MatchingTarget> findMatchingTargets();
    boolean hasOngoingRide(Long driverId);
}
