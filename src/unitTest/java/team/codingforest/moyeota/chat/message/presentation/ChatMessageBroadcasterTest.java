package team.codingforest.moyeota.chat.message.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.chat.common.redis.ChatRedisPublisher;
import team.codingforest.moyeota.chat.member.event.ChatRoomLeftEvent;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatMessageBroadcasterTest {

    private static final Long ROOM_ID = 10L;
    private static final Long USER_ID = 7L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f7a1c2e-8b4d-4c1a-9f2e-1234567890ab");

    @Mock
    private ChatRedisPublisher chatRedisPublisher;

    private final List<Runnable> pending = new ArrayList<>();
    private ChatMessageBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        AfterCommitExecutor deferred = (name, task) -> pending.add(task);
        broadcaster = new ChatMessageBroadcaster(deferred, chatRedisPublisher);
    }

    @Test
    void 커밋_전에는_발행하지_않는다() {
        broadcaster.onRoomLeft(new ChatRoomLeftEvent(USER_ID, PUBLIC_ID, ROOM_ID));

        verifyNoInteractions(chatRedisPublisher);
        assertThat(pending).hasSize(1);
    }

    @Test
    void 커밋_후_퇴장을_발행한다() {
        broadcaster.onRoomLeft(new ChatRoomLeftEvent(USER_ID, PUBLIC_ID, ROOM_ID));

        pending.forEach(Runnable::run);

        verify(chatRedisPublisher).memberLeft(ROOM_ID, USER_ID, PUBLIC_ID);
    }
}