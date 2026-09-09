package team.codingforest.moyeota.chat.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import team.codingforest.moyeota.chat.app.ChatMessageService;
import team.codingforest.moyeota.chat.app.dto.SendMessageCommand;
import team.codingforest.moyeota.chat.config.ChatPrincipal;
import team.codingforest.moyeota.chat.presentation.dto.SendMessageRequest;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class StompChatController {

    private final ChatMessageService chatMessageService;

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
}

