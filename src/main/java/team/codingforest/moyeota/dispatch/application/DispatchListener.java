package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchListener {

    private final DispatchService dispatchService;

    /** AFTER_COMMIT + @Async + @Transactional(REQUIRES_NEW) + 발행 기록(event_publication). 예외는 삼키지 않는다 - 삼키면 "완료"로 기록돼 재시도가 안 된다 */
    /** 기존 ApplicationModulerListener를 사용했을 경우에 @ASYNC와 @Transactional(REQUIRES_NEW)를 자동으로 붙여준다 이랬을 경우 이벤트를 처리하는 다른 스레드에서 커넥션을 2개(REQUIRES_NEW)와
     *  다른 서비스 로직(Trasaction)을 물고 있을 수 있기에 REQUIRES_NEW를 제거
     *  **/
    @Async
    @Transactional(propagation = Propagation.REQUIRED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchingStartedEvent event) {
        dispatchService.dispatch(event.partyId());
    }
}
