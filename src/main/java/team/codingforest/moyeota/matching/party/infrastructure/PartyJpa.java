package team.codingforest.moyeota.matching.party.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.api.dto.MatchingTarget;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.PartyVersion;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PartyJpa implements Parties {

    private final PartyJpaRepository partyJpaRepository;

    @Override
    public Optional<Party> findById(Long id) {
        return partyJpaRepository.findWithMembersById(id)
                .map(PartyEntity::toDomain);
    }

    @Override
    public Party save(Party party) {
        PartyEntity entity;

        if(party.getId() == null) {
            entity = PartyEntity.from(party);
            partyJpaRepository.save(entity);
        }
        else {
            entity = partyJpaRepository.findById(party.getId())
                    .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
            entity.update(party);                           // 더티체킹
        }

        return entity.toDomain();                           // 엔티티 -> 도메인
    }

    @Override
    public List<Party> findAllByStatus(PartyStatus status) {
        return partyJpaRepository.findAllByStatus(status).stream()
                .map(PartyEntity::toDomain).toList();
    }

    @Override
    public boolean existsOngoingByMemberId(Long memberId) {
        List<PartyStatus> ongoing = Arrays.stream(PartyStatus.values())
                .filter(PartyStatus::isOngoing)
                .toList();

        return partyJpaRepository.existsByMemberIdAndStatusIn(memberId, ongoing);
    }

    @Override
    public Optional<Party> findByIdForUpdate(Long id) {
        return partyJpaRepository.findByForUpdate(id)
                .map(PartyEntity::toDomain);
    }

    @Override
    public List<MatchingTarget> findMatchingTargets() {
        return partyJpaRepository.findTargetsByStatus(PartyStatus.MATCHING)
                .stream().map(PartyEntity::toMatchTarget).toList();
    }

    @Override
    public boolean hasOngoingRide(Long driverId) {
        return partyJpaRepository.existsByTaxiDriverIdStatus(driverId, List.of(PartyStatus.DRIVER_ASSIGNED, PartyStatus.IN_RIDE));
    }

    @Override
    public List<PartySummary> findSummariesWithinBounds(PartyStatus status, double swLat, double neLat, double swLng, double neLng, int limit) {
        return partyJpaRepository.findSummariesWithinBounds(status, swLat, neLat, swLng, neLng, Limit.of(limit));
    }

    @Override
    public Map<Long, Integer> countFinishedRides(List<Long> memberIds) {
        if(memberIds.isEmpty()) return Map.of();

        Map<Long, Integer> counts = new HashMap<>();

        for(MemberRideCount row : partyJpaRepository.countByMemberIdsAndStatus(memberIds, PartyStatus.FINISHED)) {
            counts.put(row.getMemberId(), row.getCount().intValue());
        }

        return counts;
    }

    @Override
    public List<Long> findCompletedBefore(Instant before) {
        return partyJpaRepository.findIdsByStatusAndCompletedBefore(PartyStatus.COMPLETED, before);
    }

    @Override
    public boolean existsById(Long id) {
        return partyJpaRepository.existsById(id);
    }

    @Override
    public boolean hasMember(Long partyId, Long memberId) {
        return partyJpaRepository.existsMember(partyId, memberId);
    }

    @Override
    public List<PartyVersion> findVersions(Collection<Long> ids) {
        if(ids.isEmpty()) return List.of();

        return partyJpaRepository.findVersions(ids);
    }
}
