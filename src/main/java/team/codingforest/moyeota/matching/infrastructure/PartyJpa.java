package team.codingforest.moyeota.matching.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PartyJpa implements Parties {
    private final PartyJpaRepository delegate;

    @Override
    public Optional<Party> findById(Long id) {
        return null;
    }

    @Override
    public Party save(Party party) {
        return null;
    }
}
