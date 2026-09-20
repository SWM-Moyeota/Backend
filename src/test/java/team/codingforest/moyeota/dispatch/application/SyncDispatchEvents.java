package team.codingforest.moyeota.dispatch.application;

import org.springframework.context.ApplicationEventPublisher;
import team.codingforest.moyeota.dispatch.application.event.CallAcceptedEvent;
import team.codingforest.moyeota.dispatch.application.event.CallOpenedEvent;

/**
 *  단위 테스트용 발행기 - 트랜잭션 없이 발행 즉시 리스너를 부른다.
 *  "커밋 후에만 도는가"는 DispatchEventListenerTransactionTest 가 따로 본다.
 */
class SyncDispatchEvents implements ApplicationEventPublisher {
    private final DispatchEventListener listener;

    SyncDispatchEvents(DispatchEventListener listener) {
        this.listener = listener;
    }

    @Override
    public void publishEvent(Object event) {
        if(event instanceof CallOpenedEvent e) listener.on(e);
        else if(event instanceof CallAcceptedEvent e) listener.on(e);
    }
}
