package team.codingforest.moyeota.matching.application;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.infrastructure.PartyJpaRepository;

@Service
@RequiredArgsConstructor
public class PartyApplicationService {
    private final Parties parties;

    @Transactional
    public void save() {
        
    }
}
