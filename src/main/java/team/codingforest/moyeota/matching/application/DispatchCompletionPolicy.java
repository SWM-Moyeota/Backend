package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;
import team.codingforest.moyeota.matching.domain.Party;

import java.time.Instant;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Component
@Slf4j
@RequiredArgsConstructor
public class DispatchCompletionPolicy implements PartyCompletionPolicy{

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void onCompleted(Party party) {
        party.startMatching(Instant.now());
        eventPublisher.publishEvent(new MatchingStartedEvent(party.getId()));

        log.info("매칭 시작 partyId={}, status={}", party.getId(), party.getStatus());
    }
}
