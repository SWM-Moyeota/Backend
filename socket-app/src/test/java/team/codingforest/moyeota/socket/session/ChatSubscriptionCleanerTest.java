package team.codingforest.moyeota.socket.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import team.codingforest.moyeota.socket.auth.VerifiedRooms;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatSubscriptionCleanerTest {

    private static final Long USER_ID = 7L;
    private static final Long ROOM_ID = 10L;
    private static final Long OTHER_ROOM_ID = 11L;
    private static final String SESSION_ID = "session-1";

    @Mock
    private SimpUserRegistry userRegistry;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private MessageChannel channel;

    private VerifiedRooms verifiedRooms;
    private ChatSubscriptionCleaner cleaner;

    @BeforeEach
    void setUp() {
        verifiedRooms = new VerifiedRooms();
        cleaner = new ChatSubscriptionCleaner(userRegistry, messagingTemplate, verifiedRooms);
    }

    @Test
    void 나간_방과_하위_목적지_구독만_해제한다() {
        SimpSession session = session(
                subscription("sub-1", "/sub/chat-rooms/10"),
                subscription("sub-2", "/sub/chat-rooms/10/location"),
                subscription(null, "/sub/chat-rooms/11"),
                subscription(null, "/sub/chat-rooms/100"));
        givenUser(session);
        given(messagingTemplate.getMessageChannel()).willReturn(channel);

        cleaner.unsubscribeRoom(USER_ID, ROOM_ID);

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(channel, times(2)).send(captor.capture());
        assertThat(captor.getAllValues())
                .map(SimpMessageHeaderAccessor::wrap)
                .allSatisfy(accessor -> {
                    assertThat(accessor.getMessageType()).isEqualTo(SimpMessageType.UNSUBSCRIBE);
                    assertThat(accessor.getSessionId()).isEqualTo(SESSION_ID);
                })
                .extracting(SimpMessageHeaderAccessor::getSubscriptionId)
                .containsExactlyInAnyOrder("sub-1", "sub-2");
    }

    @Test
    void 나간_방의_확인_기록만_지운다() {
        verifiedRooms.add(SESSION_ID, ROOM_ID);
        verifiedRooms.add(SESSION_ID, OTHER_ROOM_ID);
        givenUser(session());

        cleaner.unsubscribeRoom(USER_ID, ROOM_ID);

        assertThat(verifiedRooms.contains(SESSION_ID, ROOM_ID)).isFalse();
        assertThat(verifiedRooms.contains(SESSION_ID, OTHER_ROOM_ID)).isTrue();
    }

    @Test
    void 이_서버에_연결이_없으면_아무것도_하지_않는다() {
        cleaner.unsubscribeRoom(USER_ID, ROOM_ID);

        verifyNoInteractions(messagingTemplate);
    }

    private void givenUser(SimpSession session) {
        SimpUser user = mock(SimpUser.class);
        given(user.getSessions()).willReturn(Set.of(session));
        given(userRegistry.getUser(String.valueOf(USER_ID))).willReturn(user);
    }

    private static SimpSession session(SimpSubscription... subscriptions) {
        SimpSession session = mock(SimpSession.class);
        given(session.getId()).willReturn(SESSION_ID);
        given(session.getSubscriptions()).willReturn(Set.of(subscriptions));
        return session;
    }

    // 해제 대상이 아닌 구독은 id 를 읽지 않아 stub 하지 않음
    private static SimpSubscription subscription(String id, String destination) {
        SimpSubscription subscription = mock(SimpSubscription.class);
        given(subscription.getDestination()).willReturn(destination);
        if (id != null) {
            given(subscription.getId()).willReturn(id);
        }
        return subscription;
    }
}
