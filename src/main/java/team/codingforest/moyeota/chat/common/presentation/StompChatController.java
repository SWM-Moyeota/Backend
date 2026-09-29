package team.codingforest.moyeota.chat.common.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import team.codingforest.moyeota.chat.location.application.ChatLocationService;
import team.codingforest.moyeota.chat.message.application.ChatMessageService;
import team.codingforest.moyeota.chat.member.application.ChatRoomUserService;
import team.codingforest.moyeota.chat.location.dto.ChatLocationResult;
import team.codingforest.moyeota.chat.message.dto.ReadChatCommand;
import team.codingforest.moyeota.chat.message.dto.SendMessageCommand;
import team.codingforest.moyeota.chat.common.config.ChatPrincipal;
import team.codingforest.moyeota.chat.location.dto.LocationRequest;
import team.codingforest.moyeota.chat.message.dto.ReadMessageRequest;
import team.codingforest.moyeota.chat.message.dto.SendMessageRequest;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class StompChatController {

    private final ChatMessageService chatMessageService;
    private final ChatLocationService chatLocationService;
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

