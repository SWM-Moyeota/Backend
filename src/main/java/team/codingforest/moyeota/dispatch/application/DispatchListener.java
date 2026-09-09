package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchListener {

    private final DispatchService dispatchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchingStartedEvent event) {
        try {
            dispatchService.dispatch(event.partyId());
        } catch(Exception e) {
            log.error("이벤트 발송 실패 partyId={}", event.partyId(), e);
        }
    }
}
