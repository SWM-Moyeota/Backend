package team.codingforest.moyeota.chat.config;

import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final String USER_ID_HEADER = "X-User-Id";
    @Override
    public Message<?> preSend(@NonNull Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();

        if (command == null || StompCommand.DISCONNECT.equals(command)) {
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            Long userId = parseUserId(accessor.getFirstNativeHeader(USER_ID_HEADER));
            accessor.setUser(new ChatPrincipal(userId));
            return message;
        }

        if (accessor.getUser() == null) {
            throw new ChatException(ChatErrorCode.CHAT_UNAUTHORIZED);
        }

        return message;
    }

    private Long parseUserId(String header) {
        if (header == null || header.isBlank()) {
            throw new ChatException(ChatErrorCode.CHAT_UNAUTHORIZED);
        }

        try {
            return Long.parseLong(header.trim());
        }catch (NumberFormatException e) {
            throw new ChatException(ChatErrorCode.CHAT_UNAUTHORIZED);
        }
    }
}

