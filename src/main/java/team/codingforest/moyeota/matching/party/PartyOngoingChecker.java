package team.codingforest.moyeota.matching.party;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.user.api.OngoingPartyChecker;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class PartyOngoingChecker implements OngoingPartyChecker {
    private final Parties parties;
    private final DriverAccess driverAccess;

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public boolean hasOngoingParty(Long userId) {
        Optional<Long> driverId = driverAccess.lockIdByUserId(userId);

        if(parties.existsOngoingByMemberId(userId)) return true;

        return driverId
                .map(parties::hasOngoingRide)
                .orElse(false);
    }
}
