package team.codingforest.moyeota.chat.common.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.member.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.member.infrastructure.RoomLeftPayload;
import team.codingforest.moyeota.chat.message.domain.enums.ChatMessageType;
import team.codingforest.moyeota.chat.message.dto.ChatMessageResult;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.api.TokenAuthenticator;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 *  API 가 Redis 로 발행한 이벤트가 실제 STOMP 구독자에게 어떤 프레임으로 도착하는지 본다.
 *  구독 서버가 payload 를 다시 변환하지 않고 그대로 보내도 바꾸기 전(convertAndSend 로 객체를 보낼 때)과
 *  본문·content-type 이 같아야 앱이 영향을 받지 않는다. 비교 기준은 브로커 기본 변환기가 만든 결과다.
 *  인증과 방 참여 확인만 mock 으로 바꾸고 Redis 와 브로커는 실제로 돈다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatRedisStompIntegrationTest {

    private static final Long USER_ID = 7L;
    private static final Long ROOM_ID = 990_000L + System.currentTimeMillis() % 9_000L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f7a1c2e-8b4d-4c1a-9f2e-1234567890ab");
    private static final String TOKEN = "test-token";

    @LocalServerPort
    int port;
    @Autowired
    ChatRedisPublisher publisher;
    @Autowired
    SimpUserRegistry userRegistry;
    @Autowired
    @Qualifier("brokerMessageConverter")
    CompositeMessageConverter brokerMessageConverter;

    @MockitoBean
    TokenAuthenticator tokenAuthenticator;
    @MockitoBean
    ChatRoomUsers chatRoomUsers;

    private WebSocketStompClient client;
    private StompSession session;

    @BeforeEach
    void connect() throws Exception {
        given(tokenAuthenticator.authenticate(TOKEN))
                .willReturn(Optional.of(new AuthenticatedPrincipal(USER_ID, PUBLIC_ID)));
        given(chatRoomUsers.findActiveByUserIdAndChatRoomId(USER_ID, ROOM_ID))
                .willReturn(Optional.of(mock(ChatRoomUser.class)));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + TOKEN);

        client = new WebSocketStompClient(new StandardWebSocketClient());
        session = client.connectAsync("ws://localhost:" + port + "/ws-chat", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        client.stop();
    }

    @Test
    void 방_메시지는_바꾸기_전과_같은_본문과_content_type_으로_도착한다() throws Exception {
        BlockingQueue<Frame> frames = subscribe("/sub/chat-rooms/" + ROOM_ID);
        ChatMessageResult result = new ChatMessageResult(1L, ROOM_ID, 1L, PUBLIC_ID, "안녕하세요",
                ChatMessageType.TEXT, Instant.parse("2026-10-29T06:00:00.123456Z"), false);

        publisher.message(result);

        Frame frame = frames.poll(5, TimeUnit.SECONDS);
        assertThat(frame).as("Redis 를 거쳐 구독자에게 도착해야 한다").isNotNull();
        assertSameAsConvertAndSend(frame, result);
    }

    @Test
    void 퇴장하면_본인에게_알리고_방_구독을_끊는다() throws Exception {
        BlockingQueue<Frame> room = subscribe("/sub/chat-rooms/" + ROOM_ID);
        BlockingQueue<Frame> roomLeft = subscribe("/user/queue/room-left");

        publisher.memberLeft(ROOM_ID, USER_ID, PUBLIC_ID, null);

        Frame frame = roomLeft.poll(5, TimeUnit.SECONDS);
        assertThat(frame).as("본인 큐로 퇴장 알림이 와야 한다").isNotNull();
        assertSameAsConvertAndSend(frame, new RoomLeftPayload(PUBLIC_ID, ROOM_ID));

        // 구독 해제는 브로커에만 반영되고 SimpUserRegistry 에는 남아서, 메시지가 더 안 오는 것으로 확인한다
        room.clear();
        publisher.message(new ChatMessageResult(2L, ROOM_ID, 2L, PUBLIC_ID, "나간 뒤 메시지",
                ChatMessageType.TEXT, Instant.now(), false));
        assertThat(room.poll(1, TimeUnit.SECONDS)).as("나간 방 메시지는 더 오지 않아야 한다").isNull();
    }

    private void assertSameAsConvertAndSend(Frame frame, Object payload) {
        Message<?> expected = brokerMessageConverter.toMessage(payload, null);

        assertThat(new String(frame.body()))
                .isEqualTo(new String((byte[]) expected.getPayload()));
        assertThat(frame.headers().getContentType())
                .isEqualTo(expected.getHeaders().get(MessageHeaders.CONTENT_TYPE));
    }

    // SUBSCRIBE 는 응답이 없어 서버 레지스트리에 잡힐 때까지 기다린 뒤 발행한다
    private BlockingQueue<Frame> subscribe(String destination) {
        BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add(new Frame(headers, (byte[]) payload));
            }
        });

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> subscribedTo(destination));
        return frames;
    }

    private boolean subscribedTo(String destinationPrefix) {
        SimpUser user = userRegistry.getUser(String.valueOf(USER_ID));
        if (user == null) {
            return false;
        }
        return user.getSessions().stream()
                .flatMap(s -> s.getSubscriptions().stream())
                .map(SimpSubscription::getDestination)
                .anyMatch(d -> d.startsWith(destinationPrefix));
    }

    private record Frame(StompHeaders headers, byte[] body) {
    }
}
