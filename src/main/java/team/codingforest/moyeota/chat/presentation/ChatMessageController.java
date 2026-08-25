package team.codingforest.moyeota.chat.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.chat.app.ChatMessageService;
import team.codingforest.moyeota.chat.app.dto.*;
import team.codingforest.moyeota.chat.presentation.dto.SendMessageRequest;

@RestController
@RequestMapping("/api/v1/chat-rooms/{chatRoomId}/messages")
@RequiredArgsConstructor
public class ChatMessageController {
    private final ChatMessageService chatMessageService;
    
    @GetMapping
    public ChatMessageSlice getChatMessages(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size
    ) {
        FindMessageCommand command = new FindMessageCommand(userId, chatRoomId, cursor, size);
        return chatMessageService.findBefore(command);
    }

    @GetMapping("/after")
    public ChatMessageSlice getChatMessagesAfter(
            @RequestHeader("X-User-Id") Long userId,
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
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return chatMessageService.sendMessage(new SendMessageCommand(chatRoomId, userId, request.content()));
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(
            @PathVariable Long chatRoomId,
            @PathVariable Long messageId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        chatMessageService.deleteMessage(chatRoomId, messageId, userId);
    }

    @GetMapping("/search")
    public ChatMessageSlice search(
            @PathVariable Long chatRoomId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size
    ) {
        return chatMessageService.searchMessage(
                new SearchMessageCommand(userId, chatRoomId, keyword, cursor, size)
        );
    }
}
