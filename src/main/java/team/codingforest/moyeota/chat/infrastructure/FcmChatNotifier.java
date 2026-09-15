package team.codingforest.moyeota.chat.infrastructure;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
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
import java.util.Objects;

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
                // addAllFids 가 아니다 — 저장하는 값은 FCM 등록 토큰이라 addAllTokens 로 실어야 한다.
                // (fids 로 보내면 FCM 이 매 건을 거절해 성공=0, 실패=N 만 찍힌다)
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
                    logFailures(notification.chatRoomId(), result);
                }
            }
        }, MoreExecutors.directExecutor());
    }

    // 실패 건수만 남기면 원인을 알 수 없다 — 토큰이 죽은 건지, 페이로드가 잘못된 건지 구분이 안 된다.
    // 토큰 자체는 자격증명이므로 찍지 않는다.
    private void logFailures(Long chatRoomId, BatchResponse result) {
        result.getResponses().stream()
                .filter(response -> !response.isSuccessful())
                .map(SendResponse::getException)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(e -> log.warn("[채팅 알림] 실패 사유 chatRoomId={}, code={}, message={}",
                        chatRoomId, e.getMessagingErrorCode(), e.getMessage()));
    }
}