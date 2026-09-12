package team.codingforest.moyeota.chat.infrastructure;

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
import team.codingforest.moyeota.chat.domain.ChatMessageNotification;
import team.codingforest.moyeota.chat.domain.ChatNotifier;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Primary
@ConditionalOnExpression("!'${fcm.service-account-path:}'.isEmpty()")
@RequiredArgsConstructor
public class FcmChatNotifier implements ChatNotifier {

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
                .putData("type", "CHAT_MESSAGE")
                .putData("chatRoomId", String.valueOf(notification.chatRoomId()))
                .putData("messageId", String.valueOf(notification.messageId()))
                .putData("senderPublicId", String.valueOf(notification.senderPublicId()))
                .putData("senderNickname", notification.senderNickname())
                .putData("preview", notification.preview())
                .addAllFids(tokens.values())
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
            }
        }, MoreExecutors.directExecutor());
    }
}