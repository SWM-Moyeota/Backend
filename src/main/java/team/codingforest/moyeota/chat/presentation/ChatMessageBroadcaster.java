package team.codingforest.moyeota.chat.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.chat.app.event.ChatMessageDeleteEvent;
import team.codingforest.moyeota.chat.app.event.ChatMessageSentEvent;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {
    private static final String ROOM_DESTINATION = "/sub/chat-rooms/";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        messagingTemplate.convertAndSend(
                ROOM_DESTINATION + event.result().chatRoomId(),
                event.result()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageDeleted(ChatMessageDeleteEvent event) {
        messagingTemplate.convertAndSend(
                ROOM_DESTINATION + event.result().chatRoomId(),
                event.result()
        );
    }
}
