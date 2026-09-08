package team.codingforest.moyeota.chat.config;

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
import team.codingforest.moyeota.chat.domain.ChatRoomUserRepository;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.api.TokenAuthenticator;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String APP_DESTINATION_PREFIX = "/pub/";
    private static final String ROOM_DESTINATION_PREFIX = "/sub/chat-rooms/";
    private static final String ERROR_DESTINATION = "/user/queue/errors";
    private static final String ROOM_LEFT_DESTINATION = "/user/queue/room-left";

    private final ChatRoomUserRepository chatRoomUserRepository;
    private final TokenAuthenticator tokenAuthenticator;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();

        if (command == null || StompCommand.DISCONNECT.equals(command)) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            Long userId = authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION));
            accessor.setUser(new ChatPrincipal(userId));
            return message;
        }

        Long userId = currentUserId(accessor);

        if (StompCommand.SUBSCRIBE.equals(command)) {
            validateSubscribe(userId, accessor.getDestination());
        }

        if (StompCommand.SEND.equals(command)) {
            validateSend(accessor.getDestination());
        }

        return message;
    }

    private Long currentUserId(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();

        if (!(principal instanceof ChatPrincipal chatPrincipal)) {
            throw new ChatException(ChatErrorCode.CHAT_UNAUTHORIZED);
        }

        return chatPrincipal.userId();
    }

    private void validateSubscribe(Long userId, String destination) {
        if (ERROR_DESTINATION.equals(destination) || ROOM_LEFT_DESTINATION.equals(destination)) {
            return;
        }
        Long chatRoomId = parseChatRoomId(destination);

        if (chatRoomId == null) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        chatRoomUserRepository.findActiveByUserIdAndChatRoomId(userId, chatRoomId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT));
    }

    private void validateSend(String destination) {
        if (destination == null || !destination.startsWith(APP_DESTINATION_PREFIX)) {
            throw new ChatException(ChatErrorCode.CHAT_INVALID_DESTINATION);
        }
    }

    private Long parseChatRoomId(String destination) {
        if (destination == null || !destination.startsWith(ROOM_DESTINATION_PREFIX)) {
            return null;
        }

        String remainder = destination.substring(ROOM_DESTINATION_PREFIX.length());
        int slashIndex = remainder.indexOf("/");
        String rawId = slashIndex < 0 ? remainder : remainder.substring(0, slashIndex);

        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
        }
    }

    private Long authenticate(String header) {
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new ChatException(ChatErrorCode.CHAT_UNAUTHORIZED);
        }

        return tokenAuthenticator.authenticate(header.substring(BEARER_PREFIX.length()).trim())
                .map(AuthenticatedPrincipal::userId)
                .orElseThrow(() -> new ChatException(ChatErrorCode.CHAT_UNAUTHORIZED));
    }
}

