package team.codingforest.moyeota.socket.auth;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import team.codingforest.moyeota.socket.api.ChatApiClient;
import team.codingforest.moyeota.socket.common.SocketErrorCode;
import team.codingforest.moyeota.socket.common.SocketException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class StompAuthInterceptorTest {

    private static final String SESSION_ID = "session-1";
    private static final String AUTHORIZATION = "Bearer test-token";
    private static final Long ROOM_ID = 10L;
    private static final ChatPrincipal PRINCIPAL =
            new ChatPrincipal(7L, UUID.fromString("3f7a1c2e-8b4d-4c1a-9f2e-1234567890ab"));

    @Mock
    private ChatApiClient chatApiClient;

    private final MessageChannel channel = mock(MessageChannel.class);
    private VerifiedRooms verifiedRooms;
    private StompAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        verifiedRooms = new VerifiedRooms();
        interceptor = new StompAuthInterceptor(chatApiClient, verifiedRooms);
    }

    @Test
    void CONNECT_는_헤더를_그대로_넘기고_받은_사용자를_붙인다() {
        given(chatApiClient.principal(AUTHORIZATION)).willReturn(PRINCIPAL);

        Message<?> result = interceptor.preSend(connect(AUTHORIZATION), channel);

        assertThat(accessor(result).getUser()).isEqualTo(PRINCIPAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "test-token", "Basic abc"})
    void Bearer_토큰이_아니면_API_를_부르지_않고_TOKEN_INVALID_로_거절한다(String authorization) {
        assertError(() -> interceptor.preSend(connect(authorization), channel), SocketErrorCode.TOKEN_INVALID);
        verifyNoInteractions(chatApiClient);
    }

    @Test
    void 헤더가_없으면_TOKEN_INVALID_로_거절한다() {
        assertError(() -> interceptor.preSend(connect(null), channel), SocketErrorCode.TOKEN_INVALID);
        verifyNoInteractions(chatApiClient);
    }

    @Test
    void 같은_방의_하위_목적지는_API_를_한_번만_부른다() {
        interceptor.preSend(subscribe("/sub/chat-rooms/10"), channel);
        interceptor.preSend(subscribe("/sub/chat-rooms/10/location"), channel);
        interceptor.preSend(subscribe("/sub/chat-rooms/10/members"), channel);

        verify(chatApiClient, times(1)).validateParticipant(ROOM_ID, PRINCIPAL.userId());
    }

    @Test
    void 확인_기록이_지워지면_다시_묻는다() {
        interceptor.preSend(subscribe("/sub/chat-rooms/10"), channel);
        verifiedRooms.remove(SESSION_ID, ROOM_ID);

        interceptor.preSend(subscribe("/sub/chat-rooms/10"), channel);

        verify(chatApiClient, times(2)).validateParticipant(ROOM_ID, PRINCIPAL.userId());
    }

    @Test
    void 방에_없으면_확인_기록을_남기지_않는다() {
        willThrow(new SocketException(SocketErrorCode.NOT_PARTICIPANT))
                .given(chatApiClient).validateParticipant(ROOM_ID, PRINCIPAL.userId());

        assertError(() -> interceptor.preSend(subscribe("/sub/chat-rooms/10"), channel), SocketErrorCode.NOT_PARTICIPANT);
        assertThat(verifiedRooms.contains(SESSION_ID, ROOM_ID)).isFalse();
    }

    @Test
    void 본인_큐는_API_를_부르지_않는다() {
        interceptor.preSend(subscribe("/user/queue/room-left"), channel);

        verifyNoInteractions(chatApiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/sub/other", "/sub/chat-rooms/abc", "/user/8/queue/room-left", "/queue/room-left-usersession-2"})
    void 방_목적지가_아니면_INVALID_DESTINATION_으로_거절한다(String destination) {
        assertError(() -> interceptor.preSend(subscribe(destination), channel), SocketErrorCode.INVALID_DESTINATION);
        verifyNoInteractions(chatApiClient);
    }

    @Test
    void 연결_사용자가_없으면_구독을_TOKEN_INVALID_로_거절한다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(SESSION_ID);
        accessor.setDestination("/sub/chat-rooms/10");

        assertError(() -> interceptor.preSend(message(accessor), channel), SocketErrorCode.TOKEN_INVALID);
    }

    @Test
    void SEND_는_거절한다() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId(SESSION_ID);
        accessor.setUser(PRINCIPAL);
        accessor.setDestination("/pub/chat-rooms/10");

        assertError(() -> interceptor.preSend(message(accessor), channel), SocketErrorCode.SEND_NOT_SUPPORTED);
    }

    private static Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(SESSION_ID);
        if (authorization != null) {
            accessor.addNativeHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return message(accessor);
    }

    private static Message<byte[]> subscribe(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(SESSION_ID);
        accessor.setUser(PRINCIPAL);
        accessor.setDestination(destination);
        return message(accessor);
    }

    // 실제 STOMP 처리기처럼 헤더를 바꿀 수 있게 둬야 CONNECT 에서 setUser 가 반영됨
    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static StompHeaderAccessor accessor(Message<?> message) {
        return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    }

    private static void assertError(ThrowingCallable call, SocketErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(SocketException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }
}
