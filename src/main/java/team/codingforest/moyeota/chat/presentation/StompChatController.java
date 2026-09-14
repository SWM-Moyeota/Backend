package team.codingforest.moyeota.chat.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import team.codingforest.moyeota.chat.application.ChatLocationService;
import team.codingforest.moyeota.chat.application.ChatMessageService;
import team.codingforest.moyeota.chat.application.dto.ChatLocationResult;
import team.codingforest.moyeota.chat.application.dto.SendMessageCommand;
import team.codingforest.moyeota.chat.config.ChatPrincipal;
import team.codingforest.moyeota.chat.presentation.dto.LocationRequest;
import team.codingforest.moyeota.chat.presentation.dto.SendMessageRequest;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class StompChatController {

    private final ChatMessageService chatMessageService;
    private final ChatLocationService chatLocationService;

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

    @MessageMapping("/chat-rooms/{chatRoomId}/location/sync")
    @SendToUser("/queue/location-sync")
    public List<ChatLocationResult> sync(
            @DestinationVariable Long chatRoomId,
            Principal principal
    ) {
        ChatPrincipal chatPrincipal = (ChatPrincipal) principal;

        return chatLocationService.findAll(chatPrincipal.userId(), chatRoomId);
    }

    @MessageMapping("/chat-rooms/{chatRoomId}/location")
    public void shareLocation(
            @DestinationVariable Long chatRoomId,
            @Valid @Payload LocationRequest request,
            Principal principal
    ) {
        ChatPrincipal chatPrincipal = (ChatPrincipal) principal;

        chatLocationService.share(
                chatPrincipal.userId(), chatPrincipal.publicId(), chatRoomId, request.toDomain());
    }

}

