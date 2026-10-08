package team.codingforest.moyeota.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import team.codingforest.moyeota.matching.api.dto.RideCompletedEvent;
import team.codingforest.moyeota.payment.dto.RideCompletedCommand;

/**
 *  운행 종료 → 분할 청구. 예외는 삼키지 않는다 - 삼키면 event_publication 이 완료로 기록돼 결제사 장애 때 재시도가 안 된다.
 *  NOT_SUPPORTED: 결제사 HTTP 호출이 섞이므로 리스너 트랜잭션 없이 돌고 DB 작업은 단계별 독립 트랜잭션으로 한다.
 */
@Component
@RequiredArgsConstructor
public class RideCompletedListener {
    private final PaymentGroupService paymentGroupService;

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    public void on(RideCompletedEvent event) {
        paymentGroupService.openForRide(new RideCompletedCommand(event.partyId(), event.driverId(), event.fare(), event.departure(), event.destination(), event.memberIds()));
    }
}
