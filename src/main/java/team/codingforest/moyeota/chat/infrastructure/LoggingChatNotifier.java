package team.codingforest.moyeota.chat.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.domain.ChatMessageNotification;
import team.codingforest.moyeota.chat.domain.ChatNotifier;

import java.util.List;

@Slf4j
@Component
public class LoggingChatNotifier implements ChatNotifier {

    @Override
    public void notifyNewMessage(List<Long> receiverIds, ChatMessageNotification notification) {
        log.info("[채팅알림] chatRoomId={}, messageId={}, 수신자={}명",
                notification.chatRoomId(), notification.messageId(), receiverIds.size());
    }
}