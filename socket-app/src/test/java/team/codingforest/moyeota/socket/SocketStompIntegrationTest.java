package team.codingforest.moyeota.socket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import team.codingforest.moyeota.chat.contract.ChatChannels;
import team.codingforest.moyeota.chat.contract.ChatDestinations;
import team.codingforest.moyeota.chat.contract.ChatEventEnvelope;
import team.codingforest.moyeota.socket.api.ChatApiClient;
import team.codingforest.moyeota.socket.auth.ChatPrincipal;
import team.codingforest.moyeota.socket.auth.VerifiedRooms;
import team.codingforest.moyeota.socket.common.SocketErrorCode;
import team.codingforest.moyeota.socket.common.SocketException;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 앱과 같은 STOMP 클라이언트로 소켓 서버에 붙어 연결·구독·거절·Redis 전달이 실제로 어떻게 오가는지 봄
 * API 호출(ChatApiClient)만 mock 으로 바꾸고 브로커와 Redis 는 실제로 돎
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SocketStompIntegrationTest {

    private static final Long USER_ID = 7L;
    private static final Long ROOM_ID = 990_000L + System.currentTimeMillis() % 9_000L;
    private static final ChatPrincipal PRINCIPAL =
            new ChatPrincipal(USER_ID, UUID.fromString("3f7a1c2e-8b4d-4c1a-9f2e-1234567890ab"));
    private static final String AUTHORIZATION = "Bearer test-token";
    private static final String PAYLOAD = "{\"id\":1,\"content\":\"안녕\"}";

    @LocalServerPort
    int port;
    @Autowired
    StringRedisTemplate redisTemplate;
    @Autowired
    @Qualifier("chatRedisListenerContainer")   // Boot 4.1 이 @RedisListener 용 빈 컨테이너를 하나 더 만듦
    RedisMessageListenerContainer listenerContainer;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    SimpUserRegistry userRegistry;
    @Autowired
    VerifiedRooms verifiedRooms;

    @MockitoBean
    ChatApiClient chatApiClient;

    private WebSocketStompClient client;
    private StompSession session;
    private final BlockingQueue<Frame> errors = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        given(chatApiClient.principal(AUTHORIZATION)).willReturn(PRINCIPAL);
        waitUntil(listenerContainer::isListening);
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        client.stop();
        waitUntil(() -> userRegistry.getUser(String.valueOf(USER_ID)) == null);
    }

    @Test
    void 방_메시지는_API_가_발행한_바이트_그대로_도착한다() throws Exception {
        connect();
        BlockingQueue<Frame> room = subscribe(ChatDestinations.room(ROOM_ID));

        publish(ChatEventEnvelope.of(ChatEventEnvelope.TYPE_MESSAGE, ChatDestinations.room(ROOM_ID), PAYLOAD));

        Frame frame = room.poll(5, TimeUnit.SECONDS);
        assertThat(frame).as("Redis 를 거쳐 구독자에게 도착해야 함").isNotNull();
        assertThat(frame.body()).isEqualTo(PAYLOAD);
        assertThat(frame.headers().getContentType()).isEqualTo(MimeTypeUtils.APPLICATION_JSON);
    }

    @Test
    void 같은_방의_목적지_세_개를_구독해도_API_는_한_번만_묻는다() throws Exception {
        connect();
        subscribe(ChatDestinations.room(ROOM_ID));
        subscribe(ChatDestinations.location(ROOM_ID));
        subscribe(ChatDestinations.members(ROOM_ID));

        verify(chatApiClient, times(1)).validateParticipant(ROOM_ID, USER_ID);
    }

    @Test
    void 퇴장하면_본인에게_알리고_방_구독을_끊고_다시_구독하면_다시_묻는다() throws Exception {
        connect();
        BlockingQueue<Frame> room = subscribe(ChatDestinations.room(ROOM_ID));
        BlockingQueue<Frame> roomLeft = subscribe("/user/queue/room-left");

        publish(ChatEventEnvelope.roomLeft(USER_ID, ROOM_ID, "{\"chatRoomId\":" + ROOM_ID + "}"));

        assertThat(roomLeft.poll(5, TimeUnit.SECONDS)).as("본인 큐로 퇴장 알림이 와야 함").isNotNull();

        publish(ChatEventEnvelope.of(ChatEventEnvelope.TYPE_MESSAGE, ChatDestinations.room(ROOM_ID), PAYLOAD));
        assertThat(room.poll(1, TimeUnit.SECONDS)).as("나간 방 메시지는 더 오지 않아야 함").isNull();

        session.subscribe(ChatDestinations.room(ROOM_ID), new QueueingHandler(new LinkedBlockingQueue<>()));
        verify(chatApiClient, timeout(5_000).times(2)).validateParticipant(ROOM_ID, USER_ID);
    }

    @Test
    void 만료된_토큰이면_ERROR_프레임에_TOKEN_EXPIRED_를_싣는다() throws Exception {
        given(chatApiClient.principal(AUTHORIZATION)).willThrow(new SocketException(SocketErrorCode.TOKEN_EXPIRED));

        client.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(), new ErrorCollector());

        assertError(SocketErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 참여하지_않은_방을_구독하면_ERROR_프레임에_NOT_PARTICIPANT_를_싣는다() throws Exception {
        willThrow(new SocketException(SocketErrorCode.NOT_PARTICIPANT))
                .given(chatApiClient).validateParticipant(ROOM_ID, USER_ID);
        connect();

        session.subscribe(ChatDestinations.room(ROOM_ID), new QueueingHandler(new LinkedBlockingQueue<>()));

        assertError(SocketErrorCode.NOT_PARTICIPANT);
    }

    @Test
    void STOMP_로_보내면_ERROR_프레임에_SEND_NOT_SUPPORTED_를_싣는다() throws Exception {
        connect();

        session.send("/pub/chat-rooms/" + ROOM_ID, PAYLOAD.getBytes(StandardCharsets.UTF_8));

        assertError(SocketErrorCode.SEND_NOT_SUPPORTED);
    }

    @Test
    void 연결이_끊기면_세션과_확인_기록이_정리된다() throws Exception {
        connect();
        subscribe(ChatDestinations.room(ROOM_ID));
        String sessionId = serverSessionId();
        assertThat(verifiedRooms.contains(sessionId, ROOM_ID)).isTrue();

        session.disconnect();

        waitUntil(() -> userRegistry.getUser(String.valueOf(USER_ID)) == null);
        waitUntil(() -> !verifiedRooms.contains(sessionId, ROOM_ID));
    }

    private void connect() throws Exception {
        session = client.connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders(), new ErrorCollector())
                .get(5, TimeUnit.SECONDS);
    }

    private String url() {
        return "ws://localhost:" + port + "/ws-chat";
    }

    private static StompHeaders connectHeaders() {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", AUTHORIZATION);
        return headers;
    }

    // SUBSCRIBE 는 응답이 없어 서버 레지스트리에 잡힐 때까지 기다린 뒤 발행함
    private BlockingQueue<Frame> subscribe(String destination) {
        BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
        session.subscribe(destination, new QueueingHandler(frames));
        waitUntil(() -> subscribedTo(destination));
        return frames;
    }

    private boolean subscribedTo(String destination) {
        SimpUser user = userRegistry.getUser(String.valueOf(USER_ID));
        if (user == null) {
            return false;
        }
        return user.getSessions().stream()
                .flatMap(s -> s.getSubscriptions().stream())
                .map(SimpSubscription::getDestination)
                .anyMatch(destination::equals);
    }

    private String serverSessionId() {
        SimpUser user = userRegistry.getUser(String.valueOf(USER_ID));
        assertThat(user).isNotNull();
        return user.getSessions().stream().map(SimpSession::getId).findFirst().orElseThrow();
    }

    private void publish(ChatEventEnvelope envelope) {
        redisTemplate.convertAndSend(ChatChannels.CHAT, objectMapper.writeValueAsString(envelope));
    }

    private void assertError(SocketErrorCode expected) throws InterruptedException {
        Frame error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).as("ERROR 프레임이 와야 함").isNotNull();
        assertThat(error.headers().getFirst("message")).isEqualTo(expected.name());
        assertThat(error.headers().getContentType()).isEqualTo(MimeTypeUtils.APPLICATION_JSON);
        assertThat(error.body()).contains("\"code\":\"" + expected.name() + "\"");
    }

    private static void waitUntil(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("5초 안에 조건이 만족되지 않음");
            }
            Thread.onSpinWait();
        }
    }

    // 서버가 보낸 ERROR 프레임은 세션 핸들러의 handleFrame 으로 들어옴
    private class ErrorCollector extends StompSessionHandlerAdapter {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errors.add(new Frame(headers, new String((byte[]) payload, StandardCharsets.UTF_8)));
        }
    }

    private record QueueingHandler(BlockingQueue<Frame> frames) implements StompFrameHandler {
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            frames.add(new Frame(headers, new String((byte[]) payload, StandardCharsets.UTF_8)));
        }
    }

    private record Frame(StompHeaders headers, String body) {
    }
}
