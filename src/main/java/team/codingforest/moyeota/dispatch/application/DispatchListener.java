package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchListener {

    private final DispatchService dispatchService;

    /** AFTER_COMMIT + @Async + REQUIRES_NEW + 발행 기록(event_publication). 예외는 삼키지 않는다 - 삼키면 "완료"로 기록돼 재시도가 안 된다 */
    @ApplicationModuleListener
    public void on(MatchingStartedEvent event) {
        dispatchService.dispatch(event.partyId());
    }
}
