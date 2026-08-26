package team.codingforest.moyeota.dispatch.infrastructure;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.dispatch.domain.CallNotifier;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Primary
@ConditionalOnExpression("!'${fcm.service-account-path:}'.isEmpty()")
@RequiredArgsConstructor
public class FcmCallNotifier implements CallNotifier {
    private final FirebaseMessaging firebaseMessaging;
    private final DriverAccess driverAccess;

    @Override
    public void notifyCall(List<Long> driverIds, PartySummary party) {
        Map<Long, String> tokens = driverAccess.findFcmTokens(driverIds);

        if(tokens.isEmpty()) {
            log.warn("FCM 토큰이 등록된 기사가 없음 partyId={}, 후보={}명", party.id(), driverIds.size());
            return;
        }

        MulticastMessage message = MulticastMessage.builder()
                .putData("type", "CALL_OPENED")
                .putData("partyId", String.valueOf(party.id()))
                .putData("departure", party.departure())
                .putData("destination", party.destination())
                .addAllTokens(tokens.values())
                .build();

        send(message, "콜 알림", party.id());
    }

    @Override
    public void notifyCallClosed(List<Long> driverIds, Long partyId) {
        Map<Long, String> tokens = driverAccess.findFcmTokens(driverIds);

        if(tokens.isEmpty()) return;

        MulticastMessage message = MulticastMessage.builder()
                .putData("type", "CALL_CLOSED")
                .putData("partyId", String.valueOf(partyId))
                .addAllTokens(tokens.values())
                .build();

        send(message, "콜 마감", partyId);
    }

    private void send(MulticastMessage message, String label, Long partyId) {
        ApiFuture<BatchResponse> future = firebaseMessaging.sendEachForMulticastAsync(message);

        ApiFutures.addCallback(future, new ApiFutureCallback<>(){
            @Override
            public void onFailure(Throwable t) {
                log.error("[{}] 전송 실패 partyId={}", label, partyId, t);
            }

            @Override
            public void onSuccess(BatchResponse result) {
                log.info("[{}] 전송완료 partyId={}, 성공={}, 실패={}", label, partyId, result.getSuccessCount(), result.getFailureCount());
            }
        }, MoreExecutors.directExecutor());
    }
}
