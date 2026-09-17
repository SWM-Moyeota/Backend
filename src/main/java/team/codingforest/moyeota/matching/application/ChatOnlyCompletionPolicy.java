package team.codingforest.moyeota.matching.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.domain.Party;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "false")
@Component
@Slf4j
public class ChatOnlyCompletionPolicy implements PartyCompletionPolicy{
    @Override
    public void onCompleted(Party party) {
        log.info("정원 충족 - 배차 없이 대기 partyId={}", party.getId());
    }
}
