package team.codingforest.moyeota.chat.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.chat.application.ChatMessageService;
import team.codingforest.moyeota.chat.application.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.application.dto.ChatMessageSlice;
import team.codingforest.moyeota.chat.application.dto.FindMessageCommand;
import team.codingforest.moyeota.chat.application.dto.SearchMessageCommand;
import team.codingforest.moyeota.chat.application.dto.SendMessageCommand;
import team.codingforest.moyeota.chat.presentation.dto.SendMessageRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat-rooms/{chatRoomId}/messages")
@RequiredArgsConstructor
public class ChatMessageController {
    private final ChatMessageService chatMessageService;

    @GetMapping
    public ChatMessageSlice getChatMessages(
            @CurrentUser Long userId,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size
    ) {
        FindMessageCommand command = new FindMessageCommand(userId, chatRoomId, cursor, size);
        return chatMessageService.findBefore(command);
    }

    @GetMapping("/after")
    public ChatMessageSlice getChatMessagesAfter(
            @CurrentUser Long userId,
            @PathVariable Long chatRoomId,
            @RequestParam Long cursor,
            @RequestParam(defaultValue = "30") int size
    ) {
        FindMessageCommand command = new FindMessageCommand(userId, chatRoomId, cursor, size);
        return chatMessageService.findAfter(command);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResult sendMessage(
            @PathVariable Long chatRoomId,
            @CurrentUser Long userId,
            @CurrentUser UUID publicId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return chatMessageService.sendMessage(
                new SendMessageCommand(chatRoomId, userId, publicId, request.content()));
    }


    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(
            @PathVariable Long chatRoomId,
            @PathVariable Long messageId,
            @CurrentUser Long userId,
            @CurrentUser UUID publicId
    ) {
        chatMessageService.deleteMessage(chatRoomId, messageId, userId, publicId);
    }
    @GetMapping("/search")
    public ChatMessageSlice search(
            @PathVariable Long chatRoomId,
            @CurrentUser Long userId,
            @RequestParam String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size
    ) {
        return chatMessageService.searchMessage(
                new SearchMessageCommand(userId, chatRoomId, keyword, cursor, size)
        );
    }
}
