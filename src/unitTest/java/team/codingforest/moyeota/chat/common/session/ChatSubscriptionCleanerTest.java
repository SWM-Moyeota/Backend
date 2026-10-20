package team.codingforest.moyeota.chat.common.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSubscriptionCleanerTest {

    private static final Long USER_ID = 7L;
    private static final Long ROOM_ID = 12L;
    private static final String SESSION_ID = "session-1";

    @Mock
    private SimpUserRegistry userRegistry;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private MessageChannel brokerChannel;
    @InjectMocks
    private ChatSubscriptionCleaner cleaner;

    @BeforeEach
    void setUp() {
        lenient().when(messagingTemplate.getMessageChannel()).thenReturn(brokerChannel);
    }

    @Test
    void 나간_방의_구독만_해제하고_번호가_겹치는_방은_그대로_둔다() {
        connect(
                subscription("sub-room", "/sub/chat-rooms/12"),
                subscription("sub-location", "/sub/chat-rooms/12/location"),
                subscription("sub-other-room", "/sub/chat-rooms/120"),
                subscription("sub-queue", "/user/queue/room-left"));

        cleaner.unsubscribeRoom(USER_ID, ROOM_ID);

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(brokerChannel, times(2)).send(captor.capture());
        List<SimpMessageHeaderAccessor> sent = captor.getAllValues().stream()
                .map(SimpMessageHeaderAccessor::wrap)
                .toList();
        assertThat(sent).allSatisfy(accessor -> {
            assertThat(accessor.getMessageType()).isEqualTo(SimpMessageType.UNSUBSCRIBE);
            assertThat(accessor.getSessionId()).isEqualTo(SESSION_ID);
        });
        assertThat(sent).extracting(SimpMessageHeaderAccessor::getSubscriptionId)
                .containsExactlyInAnyOrder("sub-room", "sub-location");
    }

    @Test
    void 이_서버에_연결이_없는_사용자면_아무것도_보내지_않는다() {
        when(userRegistry.getUser(String.valueOf(USER_ID))).thenReturn(null);

        cleaner.unsubscribeRoom(USER_ID, ROOM_ID);

        verify(brokerChannel, never()).send(any());
    }

    private void connect(SimpSubscription... subscriptions) {
        SimpUser user = mock(SimpUser.class);
        SimpSession session = mock(SimpSession.class);
        when(userRegistry.getUser(String.valueOf(USER_ID))).thenReturn(user);
        when(user.getSessions()).thenReturn(Set.of(session));
        lenient().when(session.getId()).thenReturn(SESSION_ID);
        when(session.getSubscriptions()).thenReturn(Set.of(subscriptions));
    }

    private static SimpSubscription subscription(String id, String destination) {
        SimpSubscription subscription = mock(SimpSubscription.class);
        lenient().when(subscription.getId()).thenReturn(id);
        when(subscription.getDestination()).thenReturn(destination);
        return subscription;
    }
}
