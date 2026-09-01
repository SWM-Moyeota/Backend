package team.codingforest.moyeota.matching.infrastructure;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.matching.api.MatchingTarget;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PartyJpa implements Parties {

    private final PartyJpaRepository delegate;

    @Override
    public Optional<Party> findById(Long id) {

        return delegate.findById(id)
                .map(jpa -> jpa.toDomain());
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
    public boolean hasOngoingRide(Long driverId) {
        return delegate.existsByTaxiDriverIdStatus(driverId, List.of(PartyStatus.DRIVER_ASSIGNED, PartyStatus.IN_RIDE));
    }

    @Override
    public List<Party> findAllByStatusWithinBounds(PartyStatus status, double swLat, double neLat, double swLng, double neLng) {
        return delegate.findAllByStatusWithinBounds(status, swLat, neLat, swLng, neLng)
                .stream().map(PartyEntity::toDomain)
                .toList();
    }
}
