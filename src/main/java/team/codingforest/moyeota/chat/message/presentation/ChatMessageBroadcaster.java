package team.codingforest.moyeota.chat.message.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.common.order.RoomOrderedExecutor;
import team.codingforest.moyeota.chat.common.redis.ChatRedisPublisher;
import team.codingforest.moyeota.chat.member.event.ChatRoomJoinedEvent;
import team.codingforest.moyeota.chat.member.event.ChatRoomLeftEvent;
import team.codingforest.moyeota.chat.message.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.message.event.ChatMessageDeleteEvent;
import team.codingforest.moyeota.chat.message.event.ChatMessageSentEvent;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {

    private static final String TASK_NAME = "chat.broadcast";

    private final AfterCommitExecutor afterCommitExecutor;
    private final RoomOrderedExecutor roomOrderedExecutor;
    private final ChatRedisPublisher chatRedisPublisher;

    @EventListener
    public void onMessageSent(ChatMessageSentEvent event) {
        ChatMessageResult result = event.result();
        publishAfterCommit(result.chatRoomId(), () -> chatRedisPublisher.message(result));
    }

    @EventListener
    public void onMessageDeleted(ChatMessageDeleteEvent event) {
        ChatMessageResult result = event.result();
        publishAfterCommit(result.chatRoomId(), () -> chatRedisPublisher.message(result));
    }

    @EventListener
    public void onRoomJoined(ChatRoomJoinedEvent event) {
        publishAfterCommit(event.chatRoomId(),
                () -> chatRedisPublisher.memberJoined(event.chatRoomId(), event.userId()));
    }

    @EventListener
    public void onRoomLeft(ChatRoomLeftEvent event) {
        publishAfterCommit(event.chatRoomId(),
                () -> chatRedisPublisher.memberLeft(event.chatRoomId(), event.userId(), event.publicId()));
    }

    private void publishAfterCommit(Long chatRoomId, Runnable publish) {
        afterCommitExecutor.execute(TASK_NAME, () -> roomOrderedExecutor.execute(chatRoomId, publish));
    }
}