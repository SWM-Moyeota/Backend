package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.dispatch.application.event.CallAcceptedEvent;
import team.codingforest.moyeota.dispatch.application.event.CallOpenedEvent;
import team.codingforest.moyeota.dispatch.domain.CallCandidates;
import team.codingforest.moyeota.dispatch.domain.CallNotifier;
import team.codingforest.moyeota.dispatch.domain.DriverLocations;

/**
 *  배차 트랜잭션이 커밋된 뒤에만 Redis 후보 명단과 FCM 알림을 건드린다.
 *  트랜잭션 안에서 직접 부르면 (1) 롤백돼도 기사에게 콜/마감 알림이 이미 가 있고
 *  (2) 방 행의 FOR UPDATE 락을 잡은 채 Redis·FCM 토큰 조회까지 끝내야 락이 풀린다.
 *  AFTER_COMMIT 리스너는 커밋을 끝낸 스레드에서 돌므로 여기서 던진 예외는 이미 성공한 요청을 500 으로 만든다 - 삼키고 로그로 남긴다.
 */
@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchEventListener {
    private final CallCandidates callCandidates;
    private final DriverLocations driverLocations;
    private final CallNotifier callNotifier;

    /** 후보 등록 → 알림 순서를 지킨다. 알림을 받은 기사가 바로 수락해도 후보에 있어야 통과한다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CallOpenedEvent event) {
        try {
            callCandidates.add(event.partyId(), event.driverIds());
            callNotifier.notifyCall(event.driverIds(), event.party());

            log.info("기사 호출 partyId={}, 신규={}명", event.partyId(), event.driverIds().size());
        } catch (Exception e) {
            // 후보 등록이 안 됐으면 다음 스윕이 같은 기사를 신규로 다시 잡는다
            log.error("콜 알림 실패 partyId={}, 대상={}명", event.partyId(), event.driverIds().size(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CallAcceptedEvent event) {
        try {
            callCandidates.clear(event.partyId());
            driverLocations.remove(event.driverId());
            callNotifier.notifyCallClosed(event.losers(), event.partyId());

            log.info("콜 마감 partyId={}, driverId={}, 마감 통지={}명", event.partyId(), event.driverId(), event.losers().size());
        } catch (Exception e) {
            // 후보가 남아 있어도 배정은 이미 커밋됐다 - 늦은 수락은 DRIVER_ALREADY_ASSIGNED 로 막힌다
            log.error("콜 마감 처리 실패 partyId={}, driverId={}", event.partyId(), event.driverId(), e);
        }
    }
}
