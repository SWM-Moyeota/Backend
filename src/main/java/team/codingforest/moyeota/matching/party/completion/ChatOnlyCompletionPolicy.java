package team.codingforest.moyeota.matching.party.completion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.Party;

@Component
@Slf4j
public class ChatOnlyCompletionPolicy implements PartyCompletionPolicy{
    @Override
    public void onCompleted(Party party) {
        log.info("정원 충족 - 배차 없이 대기 partyId={}", party.getId());
    }

    @Override
    public boolean supportsReservation() {
        return true;
    }
}
