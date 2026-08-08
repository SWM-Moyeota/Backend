package team.codingforest.moyeota.matching.domain;

import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.util.List;
import java.util.Optional;

public interface Parties {
    Optional<Party> findById(Long id);
    Party save(Party party);
}
