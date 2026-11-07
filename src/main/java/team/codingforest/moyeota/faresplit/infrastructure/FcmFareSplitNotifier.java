package team.codingforest.moyeota.faresplit.infrastructure;

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
import team.codingforest.moyeota.faresplit.domain.FareSplitNotifier;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@Primary
@ConditionalOnExpression("!'${fcm.service-account-path:}'.isEmpty()")
@RequiredArgsConstructor
public class FcmFareSplitNotifier implements FareSplitNotifier {
    private final FirebaseMessaging firebaseMessaging;
    private final UserAccess userAccess;

    @Override
    public void requestTransfer(Long partyId, List<Long> senderIds, int amount) {
        send(Push.REQUEST, partyId, senderIds, amount);
    }

    @Override
    public void remindTransfer(Long partyId, List<Long> senderIds, int amount) {
        send(Push.REMINDER, partyId, senderIds, amount);
    }

    @Override
    public void requestConfirm(Long partyId, Long payerId) {
        send(Push.CONFIRM_REQUEST, partyId, List.of(payerId), null);
    }

    @Override
    public void notifyNotReceived(Long partyId, Long senderId) {
        send(Push.NOT_RECEIVED, partyId, List.of(senderId), null);
    }

    @Override
    public void notifyCompleted(Long partyId, List<Long> participantIds) {
        send(Push.COMPLETED, partyId, participantIds, null);
    }

    /** amount 는 금액을 싣는 알림(송금 요청·재요청)만 넘긴다 - Android 는 data 만 받아 앱이 문구를 만든다 */
    private void send(Push push, Long partyId, List<Long> recipientIds, Integer amount) {
        Map<Long, String> tokens = userAccess.findFcmTokens(recipientIds);

        if(tokens.isEmpty()) {
            log.warn("[{}] FCM 토큰이 등록된 받는 사람이 없음 partyId={}, 받는 사람={}명", push.label, partyId, recipientIds.size());
            return;
        }

        MulticastMessage.Builder builder = MulticastMessage.builder()
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .setApnsConfig(apnsConfig(push, partyId, amount))
                .putData("type", push.type)
                .putData("partyId", String.valueOf(partyId))
                .addAllTokens(tokens.values());

        if(amount != null) builder.putData("amount", String.valueOf(amount));

        ApiFuture<BatchResponse> future = firebaseMessaging.sendEachForMulticastAsync(builder.build());

        ApiFutures.addCallback(future, new ApiFutureCallback<>() {
            @Override
            public void onFailure(Throwable t) {
                log.error("[{}] 전송 실패 partyId={}", push.label, partyId, t);
            }

            @Override
            public void onSuccess(BatchResponse result) {
                log.info("[{}] 전송완료 partyId={}, 성공={}, 실패={}", push.label, partyId, result.getSuccessCount(), result.getFailureCount());
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     *  iOS 는 백그라운드에서 앱 코드가 돌지 않아 시스템이 이 값을 그대로 표시한다.
     */
    private ApnsConfig apnsConfig(Push push, Long partyId, Integer amount) {
        return ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .putHeader("apns-collapse-id", String.valueOf(push.collapseIdBase + partyId % 100_000))
                .setAps(Aps.builder()
                        .setAlert(ApsAlert.builder().setTitle(push.title).setBody(push.body(amount)).build())
                        .setSound("default")
                        .setThreadId("party-" + partyId)
                        .build())
                .build();
    }

    /**
     *  collapse-id 는 다른 알림(채팅 200000, 방 알림 300000~600000)과 겹치지 않게 종류마다 10만 단위 구간을 쓴다.
     *  송금 요청과 재요청은 같은 구간이라 알림 센터에 마지막 요청 하나만 남는다
     */
    private enum Push {
        REQUEST("FARE_SPLIT_REQUEST", "택시비를 나눠 내요", "%s을 보내 주세요. 받을 계좌는 앱에서 볼 수 있어요.", 700_000, "택시비 송금 요청"),
        REMINDER("FARE_SPLIT_REMINDER", "택시비를 아직 보내지 않았어요", "%s을 보낸 뒤 보냈어요를 눌러 주세요.", 700_000, "택시비 송금 재요청"),
        CONFIRM_REQUEST("FARE_SPLIT_CONFIRM_REQUEST", "택시비가 들어왔는지 확인해 주세요", "보냈다는 송금이 하루째 확인을 기다리고 있어요. 입금을 확인했다면 받았어요를 눌러 주세요.", 800_000, "입금 확인 요청"),
        NOT_RECEIVED("FARE_SPLIT_NOT_RECEIVED", "택시비가 아직 들어오지 않았대요", "받을 계좌와 금액을 확인하고 다시 보내 주세요.", 900_000, "못 받음 알림"),
        COMPLETED("FARE_SPLIT_COMPLETED", "택시비 정산이 끝났어요", "모든 송금이 확인됐어요.", 1_000_000, "정산 완료 알림");

        private final String type;
        private final String title;
        private final String body;
        private final long collapseIdBase;
        private final String label;

        Push(String type, String title, String body, long collapseIdBase, String label) {
            this.type = type;
            this.title = title;
            this.body = body;
            this.collapseIdBase = collapseIdBase;
            this.label = label;
        }

        String body(Integer amount) {
            return amount == null ? body : body.formatted(String.format(Locale.KOREA, "%,d원", amount));
        }
    }
}
