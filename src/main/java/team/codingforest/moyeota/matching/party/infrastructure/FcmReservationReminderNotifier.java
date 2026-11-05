package team.codingforest.moyeota.matching.party.infrastructure;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Primary
@ConditionalOnExpression("!'${fcm.service-account-path:}'.isEmpty()")
@RequiredArgsConstructor
public class FcmReservationReminderNotifier implements ReservationReminderNotifier {
    private static final DateTimeFormatter DEPARTURE_TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"));

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
                .setApnsConfig(apnsConfig(partyId, departureAt))
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

    /**
     *  iOS 는 백그라운드에서 앱 코드가 돌지 않아 시스템이 이 값을 그대로 표시한다 - 없으면 iOS 에서는 이 알림이 보이지 않는다.
     *  알림은 출발 30분 전 안쪽에서 나가 남은 시간이 매번 달라 남은 분 대신 출발 시각(한국 시각)을 쓴다
     */
    private ApnsConfig apnsConfig(Long partyId, Instant departureAt) {
        return ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .putHeader("apns-collapse-id", String.valueOf(600_000 + partyId % 100_000))
                .setAps(Aps.builder()
                        .setAlert(ApsAlert.builder()
                                .setTitle("곧 출발해요")
                                .setBody(DEPARTURE_TIME.format(departureAt) + " 출발이에요. 늦지 않게 출발지로 와 주세요.")
                                .build())
                        .setSound("default")
                        .setThreadId("party-" + partyId)
                        .build())
                .build();
    }
}
