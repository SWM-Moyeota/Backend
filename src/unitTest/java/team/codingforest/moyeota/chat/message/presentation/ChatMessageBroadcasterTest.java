package team.codingforest.moyeota.chat.message.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.chat.common.order.RoomOrderedExecutor;
import team.codingforest.moyeota.chat.common.redis.ChatRedisPublisher;
import team.codingforest.moyeota.chat.member.domain.ChatMember;
import team.codingforest.moyeota.chat.member.domain.MemberProvider;
import team.codingforest.moyeota.chat.member.event.ChatRoomJoinedEvent;
import team.codingforest.moyeota.chat.member.event.ChatRoomLeftEvent;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatMessageBroadcasterTest {

    private static final Long ROOM_ID = 10L;
    private static final Long USER_ID = 7L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f7a1c2e-8b4d-4c1a-9f2e-1234567890ab");
    private static final ChatMember MEMBER = new ChatMember(USER_ID, PUBLIC_ID, "영희", null);

    @Mock
    private ChatRedisPublisher chatRedisPublisher;
    @Mock
    private RoomOrderedExecutor roomOrderedExecutor;
    @Mock
    private MemberProvider memberProvider;

    private final List<Runnable> pending = new ArrayList<>();
    private ChatMessageBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        AfterCommitExecutor deferred = (name, task) -> pending.add(task);
        broadcaster = new ChatMessageBroadcaster(deferred, roomOrderedExecutor, chatRedisPublisher, memberProvider);
    }

    @Test
    void 커밋_전에는_발행하지_않는다() {
        broadcaster.onRoomLeft(new ChatRoomLeftEvent(USER_ID, PUBLIC_ID, ROOM_ID));

        verifyNoInteractions(chatRedisPublisher, memberProvider);
        assertThat(pending).hasSize(1);
    }

    @Test
    void 커밋_후_방별_줄을_거쳐_퇴장을_발행한다() {
        given(memberProvider.findMembers(List.of(USER_ID))).willReturn(Map.of(USER_ID, MEMBER));
        runImmediately();
        broadcaster.onRoomLeft(new ChatRoomLeftEvent(USER_ID, PUBLIC_ID, ROOM_ID));

        pending.forEach(Runnable::run);

        verify(roomOrderedExecutor).execute(eq(ROOM_ID), any());
        verify(chatRedisPublisher).memberLeft(ROOM_ID, USER_ID, PUBLIC_ID, MEMBER);
    }

    @Test
    void 회원_조회는_방별_줄에_넣기_전에_끝낸다() {
        // 줄은 스레드 하나라 그 안에서 DB 를 기다리면 같은 줄의 다른 방 방송까지 밀린다
        given(memberProvider.findMembers(List.of(USER_ID))).willReturn(Map.of(USER_ID, MEMBER));
        broadcaster.onRoomJoined(new ChatRoomJoinedEvent(USER_ID, ROOM_ID));

        pending.forEach(Runnable::run);   // 줄에 넣기만 하고 실행하지 않음

        verify(memberProvider).findMembers(List.of(USER_ID));
        verify(roomOrderedExecutor).execute(eq(ROOM_ID), any());
        verifyNoInteractions(chatRedisPublisher);
    }

    private void runImmediately() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(roomOrderedExecutor).execute(any(), any());
    }
}
