package team.codingforest.moyeota.dispatch.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.dispatch.domain.CallNotifier;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.List;


/**
 *  FCM(Firebase cloud messaging) 연동 전까지 로그로 대체해서 구현
 */
@Slf4j
@Component
public class LoggingCallNotifier implements CallNotifier {

    @Override
    public void notifyCall(List<Long> driverIds, PartySummary party) {
        log.info("[콜 알림] 기사들={}, 출발={}, 도착={}", driverIds, party.departure(), party.destination());
    }

    @Override
    public void notifyCallClosed(List<Long> driverIds, Long partyId) {
        log.info("[콜 마감] 기사들={}, partyId={}", driverIds, partyId);
    }
}
