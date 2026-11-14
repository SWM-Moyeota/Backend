package team.codingforest.moyeota.socket.redis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.util.MimeTypeUtils;
import team.codingforest.moyeota.chat.contract.ChatChannels;
import team.codingforest.moyeota.chat.contract.ChatDestinations;
import team.codingforest.moyeota.chat.contract.ChatEventEnvelope;
import team.codingforest.moyeota.socket.order.ChatSeqOrderer;
import team.codingforest.moyeota.socket.session.ChatSubscriptionCleaner;
import tools.jackson.databind.ObjectMapper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ChatRedisSubscriberTest {

    private static final Long ROOM_ID = 10L;
    private static final Long USER_ID = 7L;
    private static final String PAYLOAD = "{\"id\":1,\"content\":\"안녕\"}";

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ChatSubscriptionCleaner subscriptionCleaner;
    @Mock
    private ChatSeqOrderer seqOrderer;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private ChatRedisSubscriber subscriber;

    @Test
    void 봉투의_목적지로_JSON_을_그대로_보낸다() {
        subscriber.onMessage(redisMessage(
                ChatEventEnvelope.of(ChatEventEnvelope.TYPE_MESSAGE, ChatDestinations.room(ROOM_ID), PAYLOAD)), null);

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.captor();
        verify(messagingTemplate).send(eq("/sub/chat-rooms/10"), captor.capture());
        assertThat(new String((byte[]) captor.getValue().getPayload(), UTF_8)).isEqualTo(PAYLOAD);
        assertThat(captor.getValue().getHeaders().get(MessageHeaders.CONTENT_TYPE))
                .isEqualTo(MimeTypeUtils.APPLICATION_JSON);
        verifyNoInteractions(subscriptionCleaner);
    }

    @Test
    void 순번이_실린_새_메시지는_순서_맞추기를_거쳐_보낸다() {
        subscriber.onMessage(redisMessage(ChatEventEnvelope.message(ROOM_ID, 5L, PAYLOAD)), null);

        ArgumentCaptor<Runnable> delivery = ArgumentCaptor.captor();
        verify(seqOrderer).offer(eq(ROOM_ID), eq(5L), delivery.capture());
        verifyNoInteractions(messagingTemplate);

        delivery.getValue().run();
        verify(messagingTemplate).send(eq("/sub/chat-rooms/10"), any(Message.class));
    }

    @Test
    void 순번이_없는_메시지는_바로_보낸다() {
        subscriber.onMessage(redisMessage(
                ChatEventEnvelope.of(ChatEventEnvelope.TYPE_MESSAGE, ChatDestinations.room(ROOM_ID), PAYLOAD)), null);

        verify(messagingTemplate).send(eq("/sub/chat-rooms/10"), any(Message.class));
        verifyNoInteractions(seqOrderer);
    }

    @Test
    void 퇴장_이벤트면_방_구독을_먼저_끊고_본인에게_보낸다() {
        subscriber.onMessage(redisMessage(ChatEventEnvelope.roomLeft(USER_ID, ROOM_ID, PAYLOAD)), null);

        InOrder order = inOrder(subscriptionCleaner, messagingTemplate);
        order.verify(subscriptionCleaner).unsubscribeRoom(USER_ID, ROOM_ID);
        order.verify(messagingTemplate).send(eq("/user/7/queue/room-left"), any(Message.class));
    }

    @Test
    void 목적지가_없으면_보내지_않는다() {
        subscriber.onMessage(redisMessage(
                new ChatEventEnvelope(ChatEventEnvelope.TYPE_MESSAGE, null, null, null, null, PAYLOAD)), null);

        verifyNoInteractions(messagingTemplate, subscriptionCleaner);
    }

    @Test
    void 봉투를_읽지_못해도_예외를_밖으로_던지지_않는다() {
        subscriber.onMessage(new DefaultMessage(ChatChannels.CHAT.getBytes(UTF_8), "not-json".getBytes(UTF_8)), null);

        verifyNoInteractions(messagingTemplate, subscriptionCleaner);
    }

    private DefaultMessage redisMessage(ChatEventEnvelope envelope) {
        return new DefaultMessage(ChatChannels.CHAT.getBytes(UTF_8), objectMapper.writeValueAsBytes(envelope));
    }
}
