package team.codingforest.moyeota.dispatch.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.dispatch.domain.PassengerNotifier;

@Slf4j
@Component
public class LoggingPassengerNotifier implements PassengerNotifier {
    @Override
    public void notifyDriverArrived(Long partyId) {
        log.info("[승객알림] 기사님이 근처에 도착했어요 partyId={}", partyId);
    }
}
