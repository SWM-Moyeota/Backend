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
import team.codingforest.moyeota.dispatch.domain.PassengerNotifier;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;
import java.util.Map;

// TODO 추후 알림 기능 확장될 수 있음
@Slf4j
@Component
@Primary
@ConditionalOnExpression("!'${fcm.service-account-path:}'.isEmpty()")
@RequiredArgsConstructor
public class FcmPassengerNotifier implements PassengerNotifier {
    private final FirebaseMessaging firebaseMessaging;
    private final PartyAccess partyAccess;
    private final UserAccess userAccess;

    @Override
    public void notifyDriverArrived(Long partyId) {
        List<Long> memberIds = partyAccess.findMemberIds(partyId);
        Map<Long, String> tokens = userAccess.findFcmTokens(memberIds);

        if(tokens.isEmpty()) {
            log.warn("FCM 토큰이 등록된 승객이 없음 partyId={}, 구성원={}명", partyId, memberIds.size());
            return;
        }

        MulticastMessage message = MulticastMessage.builder()
                .setAndroidConfig(com.google.firebase.messaging.AndroidConfig.builder()
                        .setPriority(com.google.firebase.messaging.AndroidConfig.Priority.HIGH)   // 데이터 메시지 기본 NORMAL은 백그라운드 ~90초 지연 - 콜/도착 알림은 즉시성 필수
                        .build())
                .putData("type", "DRIVER_ARRIVED")
                .putData("partyId", String.valueOf(partyId))
                .addAllTokens(tokens.values())
                .build();

        ApiFuture<BatchResponse> future = firebaseMessaging.sendEachForMulticastAsync(message);

        ApiFutures.addCallback(future, new ApiFutureCallback<>() {
            @Override
            public void onFailure(Throwable t) {
                log.error("[기사 도착 알림] 전송 실패 partyId={}", partyId, t);
            }

            @Override
            public void onSuccess(BatchResponse result) {
                log.info("[기사 도착 알림] 전송완료 partyId={}, 성공={}, 실패={}", partyId, result.getSuccessCount(), result.getFailureCount());
            }
        }, MoreExecutors.directExecutor());
    }
}
