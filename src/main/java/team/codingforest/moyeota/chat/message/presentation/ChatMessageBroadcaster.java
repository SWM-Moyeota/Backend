package team.codingforest.moyeota.chat.message.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.common.redis.ChatRedisPublisher;
import team.codingforest.moyeota.chat.member.event.ChatRoomJoinedEvent;
import team.codingforest.moyeota.chat.member.event.ChatRoomLeftEvent;
import team.codingforest.moyeota.chat.message.event.ChatMessageDeleteEvent;
import team.codingforest.moyeota.chat.message.event.ChatMessageSentEvent;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;

@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {

    private static final String TASK_NAME = "chat.broadcast";

    private final AfterCommitExecutor afterCommitExecutor;
    private final ChatRedisPublisher chatRedisPublisher;

    @EventListener
    public void onMessageSent(ChatMessageSentEvent event) {
        afterCommitExecutor.execute(TASK_NAME, () -> chatRedisPublisher.message(event.result()));
    }

    @EventListener
    public void onMessageDeleted(ChatMessageDeleteEvent event) {
        afterCommitExecutor.execute(TASK_NAME, () -> chatRedisPublisher.message(event.result()));
    }

    @EventListener
    public void onRoomJoined(ChatRoomJoinedEvent event) {
        afterCommitExecutor.execute(TASK_NAME,
                () -> chatRedisPublisher.memberJoined(event.chatRoomId(), event.userId()));
    }

    @EventListener
    public void onRoomLeft(ChatRoomLeftEvent event) {
        afterCommitExecutor.execute(TASK_NAME,
                () -> chatRedisPublisher.memberLeft(event.chatRoomId(), event.userId(), event.publicId()));
    }
}