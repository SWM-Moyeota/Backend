package team.codingforest.moyeota.chat.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import team.codingforest.moyeota.chat.application.ChatMessageService;
import team.codingforest.moyeota.chat.application.ChatRoomUserService;
import team.codingforest.moyeota.chat.application.dto.ReadChatCommand;
import team.codingforest.moyeota.chat.application.dto.SendMessageCommand;
import team.codingforest.moyeota.chat.config.ChatPrincipal;
import team.codingforest.moyeota.chat.presentation.dto.ReadMessageRequest;
import team.codingforest.moyeota.chat.presentation.dto.SendMessageRequest;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class StompChatController {

    private final ChatMessageService chatMessageService;
    private final ChatRoomUserService chatRoomUserService;

    @MessageMapping("/chat-rooms/{chatRoomId}/messages")
    public void sendMessage(
            @DestinationVariable Long chatRoomId,
            @Valid @Payload SendMessageRequest request,
            Principal principal
    ) {
        ChatPrincipal chatPrincipal = (ChatPrincipal) principal;

        chatMessageService.sendMessage(new SendMessageCommand(
                chatRoomId, chatPrincipal.userId(), chatPrincipal.publicId(), request.content()));
    }

    @MessageMapping("/chat-rooms/{chatRoomId}/read")
    public void readMessage(
            @DestinationVariable Long chatRoomId,
            @Valid @Payload ReadMessageRequest request,
            Principal principal
    ) {
        ChatPrincipal chatPrincipal = (ChatPrincipal) principal;

        chatRoomUserService.read(new ReadChatCommand(chatPrincipal.userId(), chatRoomId, request.lastReadMessageId()));
    }
}

