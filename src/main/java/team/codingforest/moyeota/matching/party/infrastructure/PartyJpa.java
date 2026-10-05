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
import team.codingforest.moyeota.matching.party.domain.PartyStatusSnapshot;
import team.codingforest.moyeota.matching.party.domain.PartySummary;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PartyJpa implements Parties {

    private final PartyJpaRepository delegate;

    @Override
    public Optional<Party> findById(Long id) {
        return delegate.findWithMembersById(id)
                .map(PartyEntity::toDomain);
    }

    @Override
    public Party save(Party party) {
        PartyEntity entity;

        if(party.getId() == null) {
            entity = PartyEntity.from(party);
            delegate.save(entity);
        }
        else {
            entity = delegate.findById(party.getId())
                    .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
            entity.update(party);                           // 더티체킹
        }

        return entity.toDomain();                           // 엔티티 -> 도메인
    }

    @Override
    public List<Party> findAllByStatus(PartyStatus status) {
        return delegate.findAllByStatus(status).stream()
                .map(PartyEntity::toDomain).toList();
    }

    @Override
    public boolean existsOngoingByMemberId(Long memberId) {
        List<PartyStatus> ongoing = Arrays.stream(PartyStatus.values())
                .filter(PartyStatus::isOngoing)
                .toList();

        return delegate.existsByMemberIdAndStatusIn(memberId, ongoing);
    }

    @Override
    public Optional<Party> findByIdForUpdate(Long id) {
        return delegate.findByForUpdate(id)
                .map(PartyEntity::toDomain);
    }

    @Override
    public List<MatchingTarget> findMatchingTargets() {
        return delegate.findTargetsByStatus(PartyStatus.MATCHING)
                .stream().map(PartyEntity::toMatchTarget).toList();
    }

    @Override
    public Optional<PartyStatusSnapshot> findStatusSnapshotById(Long id) {
        List<PartyStatusRow> rows = delegate.findStatusRowsById(id);

        if(rows.isEmpty()) return Optional.empty();

        List<Long> memberIds = rows.stream()
                .map(PartyStatusRow::getMemberId)
                .filter(Objects::nonNull)           // 멤버가 없는 방(마지막 사람이 나가 CANCELED)
                .toList();

        return Optional.of(new PartyStatusSnapshot(id, rows.get(0).getStatus(), memberIds));
    }

    @Override
    public boolean hasOngoingRide(Long driverId) {
        return delegate.existsByTaxiDriverIdStatus(driverId, List.of(PartyStatus.DRIVER_ASSIGNED, PartyStatus.IN_RIDE));
    }

    @Override
    public List<PartySummary> findSummariesWithinBounds(PartyStatus status, double swLat, double neLat, double swLng, double neLng, int limit) {
        return delegate.findSummariesWithinBounds(status, swLat, neLat, swLng, neLng, Limit.of(limit));
    }

    @Override
    public Map<Long, Integer> countFinishedRides(List<Long> memberIds) {
        if(memberIds.isEmpty()) return Map.of();

        Map<Long, Integer> counts = new HashMap<>();

        for(MemberRideCount row : delegate.countByMemberIdsAndStatus(memberIds, PartyStatus.FINISHED)) {
            counts.put(row.getMemberId(), row.getCount().intValue());
        }

        return counts;
    }

    @Override
    public List<Long> findCompletedBefore(Instant before) {
        return delegate.findIdsByStatusAndCompletedBefore(PartyStatus.COMPLETED, before);
    }
}
