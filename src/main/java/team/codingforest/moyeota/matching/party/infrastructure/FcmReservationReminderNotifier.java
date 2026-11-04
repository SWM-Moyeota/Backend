package team.codingforest.moyeota.matching.party.infrastructure;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.ReservationReminderNotifier;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Primary
@ConditionalOnExpression("!'${fcm.service-account-path:}'.isEmpty()")
@RequiredArgsConstructor
public class FcmReservationReminderNotifier implements ReservationReminderNotifier {
    private final FirebaseMessaging firebaseMessaging;
    private final UserAccess userAccess;

    @Override
    public void notifyDepartureSoon(Long partyId, List<Long> memberIds, Instant departureAt) {
        Map<Long, String> tokens = userAccess.findFcmTokens(memberIds);

        if(tokens.isEmpty()) {
            log.warn("FCM 토큰이 등록된 구성원이 없음 partyId={}, 구성원={}명", partyId, memberIds.size());
            return;
        }

        MulticastMessage message = MulticastMessage.builder()
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .putData("type", "RESERVATION_DEPARTURE_SOON")
                .putData("partyId", String.valueOf(partyId))
                .putData("departureAt", departureAt.toString())
                .addAllTokens(tokens.values())
                .build();

        ApiFuture<BatchResponse> future = firebaseMessaging.sendEachForMulticastAsync(message);

        ApiFutures.addCallback(future, new ApiFutureCallback<>() {
            @Override
            public void onFailure(Throwable t) {
                log.error("[출발 전 알림] 전송 실패 partyId={}", partyId, t);
            }

            @Override
            public void onSuccess(BatchResponse result) {
                log.info("[출발 전 알림] 전송완료 partyId={}, 성공={}, 실패={}", partyId, result.getSuccessCount(), result.getFailureCount());
            }
        }, MoreExecutors.directExecutor());
    }
}
