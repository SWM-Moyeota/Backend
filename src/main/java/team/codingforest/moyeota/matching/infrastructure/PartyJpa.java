package team.codingforest.moyeota.matching.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

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
        PartyEntity entity = PartyEntity.from(party);       // 도메인 -> 엔티티
        delegate.save(entity);                              // 엔티티 저장
        return entity.toDomain();                           // 엔티티 -> 도메인
    }

    @Override
    public List<Party> findAllByStatus(PartyStatus status) {
        return delegate.findAllByStatus(status).stream()
                .map(PartyEntity::toDomain).toList();
    }
}
