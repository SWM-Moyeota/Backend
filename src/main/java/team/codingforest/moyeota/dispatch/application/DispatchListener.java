package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;

@Component
@RequiredArgsConstructor
public class DispatchListener {

    private final DispatchService dispatchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchingStartedEvent event) {
        dispatchService.dispatch(event.partyId());
    }
}
