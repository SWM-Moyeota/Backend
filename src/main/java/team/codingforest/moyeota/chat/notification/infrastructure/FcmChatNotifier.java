package team.codingforest.moyeota.chat.notification.infrastructure;

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
import org.springframework.util.StringUtils;
import team.codingforest.moyeota.chat.notification.domain.ChatMessageNotification;
import team.codingforest.moyeota.chat.notification.domain.ChatNotifier;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Primary
@ConditionalOnExpression("!'${fcm.service-account-path:}'.isEmpty()")
@RequiredArgsConstructor
public class FcmChatNotifier implements ChatNotifier {

    private static final String DEFAULT_TITLE = "동승자";
    private static final String DEFAULT_BODY = "새 메시지가 도착했어요";

    private final FirebaseMessaging firebaseMessaging;
    private final UserAccess userAccess;

    @Override
    public void notifyNewMessage(List<Long> receiverIds, ChatMessageNotification notification) {
        Map<Long, String> tokens = userAccess.findFcmTokens(receiverIds);

        if (tokens.isEmpty()) {
            log.warn("FCM 토큰이 등록된 수신자가 없음 chatRoomId={}, 수신자={}명",
                    notification.chatRoomId(), receiverIds.size());
            return;
        }

        MulticastMessage message = MulticastMessage.builder()
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                .setApnsConfig(apnsConfig(notification))
                .putData("type", "CHAT_MESSAGE")
                .putData("chatRoomId", String.valueOf(notification.chatRoomId()))
                .putData("messageId", String.valueOf(notification.messageId()))
                .putData("seq", String.valueOf(notification.seq()))
                .putData("senderPublicId", String.valueOf(notification.senderPublicId()))
                .putData("senderNickname", notification.senderNickname())
                .putData("preview", notification.preview())
                .addAllTokens(tokens.values())
                .build();

        ApiFuture<BatchResponse> future = firebaseMessaging.sendEachForMulticastAsync(message);

        ApiFutures.addCallback(future, new ApiFutureCallback<>() {
            @Override
            public void onFailure(Throwable t) {
                log.error("[채팅 알림] 전송 실패 chatRoomId={}", notification.chatRoomId(), t);
            }

            @Override
            public void onSuccess(BatchResponse result) {
                log.info("[채팅 알림] 전송완료 chatRoomId={}, 성공={}, 실패={}",
                        notification.chatRoomId(), result.getSuccessCount(), result.getFailureCount());

                if (result.getFailureCount() > 0) {
                    result.getResponses().stream()
                            .filter(r -> !r.isSuccessful())
                            .forEach(r -> log.warn("[채팅 알림] 개별 실패 chatRoomId={}, 사유={}",
                                    notification.chatRoomId(),
                                    r.getException() == null ? "unknown" : r.getException().getMessage()));
                }
            }
        }, MoreExecutors.directExecutor());
    }

    /** iOS 는 백그라운드에서 앱 코드가 돌지 않아 시스템이 이 값을 그대로 표시한다. collapse-id 는 앱이 전면에서 만드는 알림 식별자와 같은 공식이다 */
    private ApnsConfig apnsConfig(ChatMessageNotification notification) {
        String title = StringUtils.hasText(notification.senderNickname()) ? notification.senderNickname() : DEFAULT_TITLE;
        String body = StringUtils.hasText(notification.preview()) ? notification.preview() : DEFAULT_BODY;

        return ApnsConfig.builder()
                .putHeader("apns-push-type", "alert")
                .putHeader("apns-priority", "10")
                .putHeader("apns-collapse-id", String.valueOf(200000 + notification.chatRoomId() % 100000))
                .setAps(Aps.builder()
                        .setAlert(ApsAlert.builder().setTitle(title).setBody(body).build())
                        .setSound("default")
                        .setThreadId("chat-" + notification.chatRoomId())
                        .build())
                .build();
    }
}