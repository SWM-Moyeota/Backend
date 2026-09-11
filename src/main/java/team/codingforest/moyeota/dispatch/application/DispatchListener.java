package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class DispatchListener {

    private final DispatchService dispatchService;

    @ApplicationModuleListener
    public void on(MatchingStartedEvent event) {
        dispatchService.dispatch(event.partyId());
    }
}
