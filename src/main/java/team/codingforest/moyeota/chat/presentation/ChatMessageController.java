package team.codingforest.moyeota.chat.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.chat.app.ChatMessageService;
import team.codingforest.moyeota.chat.app.dto.ChatMessageSlice;

@RestController
@RequestMapping("/api/chat-rooms/{chatRoomId}/messages")
@RequiredArgsConstructor
public class ChatMessageController {
    private final ChatMessageService chatMessageService;
    
    @GetMapping
    public ChatMessageSlice getChatMessages(
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int size
    ) {
        return chatMessageService.findBefore(chatRoomId, cursor, size);
    }
}
