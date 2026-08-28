package team.codingforest.moyeota.matching.infrastructure;

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
                    .orElseThrow(() -> new IllegalArgumentException("해당 방이 존재하지 않습니다."));
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
}
