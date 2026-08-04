package team.codingforest.moyeota.matching.domain;

import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface Parties {
    Optional<Party> findById(Long id);
    Party save(Party party);
}
