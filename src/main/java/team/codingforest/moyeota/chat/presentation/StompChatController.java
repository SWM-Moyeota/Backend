package team.codingforest.moyeota.chat.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import team.codingforest.moyeota.chat.app.ChatMessageService;
import team.codingforest.moyeota.chat.app.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.app.dto.SendMessageCommand;
import team.codingforest.moyeota.chat.presentation.dto.SendMessageRequest;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class StompChatController {

    private static final String ROOM_DESTINATION = "/sub/chat-rooms/";
    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat-rooms/{chatRoomId}/messages")
    public void sendMessage(
            @DestinationVariable Long chatRoomId,
            @Valid @Payload SendMessageRequest request,
            Principal principal
            ) {
        Long userId = Long.parseLong(principal.getName());

        ChatMessageResult result = chatMessageService.sendMessage(
                new SendMessageCommand(chatRoomId, userId, request.content())
        );

        messagingTemplate.convertAndSend(ROOM_DESTINATION + chatRoomId, result);
    }
}

