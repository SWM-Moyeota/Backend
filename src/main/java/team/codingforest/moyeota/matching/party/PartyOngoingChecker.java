package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.user.api.OngoingPartyChecker;

@Component
@RequiredArgsConstructor
class PartyOngoingChecker implements OngoingPartyChecker {
    private final Parties parties;

    @Transactional(readOnly = true)
    @Override
    public boolean hasOngoingParty(Long userId) {
        return parties.existsOngoingByMemberId(userId);
    }
}
