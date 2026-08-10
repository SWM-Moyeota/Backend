package team.codingforest.moyeota.matching.domain;

import java.util.List;
import java.util.Optional;

public interface LocationShares {
    LocationShare save(LocationShare share);
    Optional<LocationShare> findOngoing(Long partyId, Long memberId);
    List<LocationShare> findAllOngoingByPartyId(Long partyId);
}
