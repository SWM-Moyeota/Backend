package team.codingforest.moyeota.socket.auth;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.contract.ChatDestinations;
import team.codingforest.moyeota.socket.api.ChatApiClient;
import team.codingforest.moyeota.socket.common.SocketErrorCode;
import team.codingforest.moyeota.socket.common.SocketException;

/**
 * CONNECT 는 토큰 주인을, SUBSCRIBE 는 방 참여 여부를 API 에 물어 허용함
 * 보내기는 REST 로만 받으므로 SEND 는 거절함, UNSUBSCRIBE·DISCONNECT 는 API 를 부르지 않음
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_QUEUE_PREFIX = "/user/queue/";

    private final ChatApiClient chatApiClient;
    private final VerifiedRooms verifiedRooms;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) return message;

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            validateSubscribe(accessor);
        }

        if (StompCommand.SEND.equals(command)) {
            throw new SocketException(SocketErrorCode.SEND_NOT_SUPPORTED);
        }

        return message;
    }

    // 토큰 검증은 API 가 하므로 헤더 모양만 보고 그대로 넘김
    private ChatPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new SocketException(SocketErrorCode.TOKEN_INVALID);
        }

        return chatApiClient.principal(authorization);
    }

    private void validateSubscribe(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof ChatPrincipal principal)) {
            throw new SocketException(SocketErrorCode.TOKEN_INVALID);
        }

        String destination = accessor.getDestination();

        // /user/queue/** 는 브로커가 세션별 목적지로 바꿔서 본인 것만 받음
        if (destination != null && destination.startsWith(USER_QUEUE_PREFIX)) {
            return;
        }

        Long chatRoomId = parseChatRoomId(destination);
        String sessionId = accessor.getSessionId();

        if (verifiedRooms.contains(sessionId, chatRoomId)) {
            return;
        }

        chatApiClient.validateParticipant(chatRoomId, principal.userId());
        verifiedRooms.add(sessionId, chatRoomId);
    }

    // /sub/chat-rooms/{id} 와 그 아래 /location·/members 에서 방 id 를 꺼냄
    private Long parseChatRoomId(String destination) {
        if (destination == null || !destination.startsWith(ChatDestinations.ROOM_PREFIX)) {
            throw new SocketException(SocketErrorCode.INVALID_DESTINATION);
        }

        String remainder = destination.substring(ChatDestinations.ROOM_PREFIX.length());
        int slashIndex = remainder.indexOf('/');
        String rawId = slashIndex < 0 ? remainder : remainder.substring(0, slashIndex);

        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            throw new SocketException(SocketErrorCode.INVALID_DESTINATION);
        }
    }
}
