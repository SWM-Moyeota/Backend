package team.codingforest.moyeota.chat.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.chat.application.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.application.event.ChatMessageSentEvent;
import team.codingforest.moyeota.chat.domain.ChatMember;
import team.codingforest.moyeota.chat.domain.ChatMessageNotification;
import team.codingforest.moyeota.chat.domain.ChatMessageType;
import team.codingforest.moyeota.chat.domain.ChatNotifier;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.domain.MemberProvider;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatNotificationListener {
    private final ChatRoomUsers chatRoomUsers;
    private final MemberProvider memberProvider;
    private final ChatNotifier chatNotifier;

    private static final int PREVIEW_LENGTH = 30;
    private static final String UNKNOWN_SENDER = "알 수 없음";
    private static final String LOCATION_PREVIEW = "위치를 공유했습니다";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onMessageSent(ChatMessageSentEvent event) {
        try {
            notifyReceiver(event);
        } catch (Exception e) {
            log.error("채팅 푸시 알림 전송 실패 chatRoomId={}, messageId={}",
                    event.result().chatRoomId(), event.result().id(), e);
        }
    }

    private void notifyReceiver(ChatMessageSentEvent event) {
        ChatMessageResult result = event.result();

        List<Long> receiverIds = chatRoomUsers.findAllByChatRoomId(result.chatRoomId()).stream()
                .filter(user -> !user.hasLeft())
                .filter(user -> !user.isNotificationMuted())
                .map(ChatRoomUser::getUserId)
                .filter(userId -> !userId.equals(event.senderId()))
                .toList();

        if (receiverIds.isEmpty()) {
            log.debug("푸시 수신자 없음 chatRoomId={}", result.chatRoomId());
            return;
        }

        ChatMember sender = memberProvider.findMembers(List.of(event.senderId())).get(event.senderId());

        chatNotifier.notifyNewMessage(receiverIds, new ChatMessageNotification(
                result.chatRoomId(),
                result.id(),
                result.publicId(),
                sender == null ? UNKNOWN_SENDER : sender.nickname(),
                preview(result)
        ));
    }

    private String preview(ChatMessageResult result) {
        if (result.type() == ChatMessageType.LOCATION) {
            return LOCATION_PREVIEW;
        }
        String content = result.content();
        if (content == null) {
            return "";
        }
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "...";
    }

}
