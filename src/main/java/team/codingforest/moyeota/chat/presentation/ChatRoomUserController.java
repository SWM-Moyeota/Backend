package team.codingforest.moyeota.chat.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.chat.app.ChatRoomUserService;
import team.codingforest.moyeota.chat.app.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.app.dto.ChatRoomUserResult;
import team.codingforest.moyeota.chat.app.dto.ReadChatCommand;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomUserController {
    private final ChatRoomUserService chatRoomUserService;

    @GetMapping("/me")
    public List<ChatRoomUserResult> getMyActiveRooms(@RequestHeader("X-User-Id") Long userId) {
        return chatRoomUserService.findMyActiveRooms(userId);
    }

    @PostMapping("/{chatRoomId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public void join(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long chatRoomId
    ) {
        chatRoomUserService.join(new ChatRoomCommand(chatRoomId, userId));
    }

    @DeleteMapping("/{chatRoomId}/users")
    @ResponseStatus(HttpStatus.OK)
    public void leave(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long chatRoomId
    ) {
        chatRoomUserService.leave(new ChatRoomCommand(chatRoomId, userId));
    }

    @PostMapping("/{chatRoomId}/users/read/{readMessageId}")
    @ResponseStatus(HttpStatus.OK)
    public void readChatRoom(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long chatRoomId,
            @PathVariable Long readMessageId
    ) {
        chatRoomUserService.read(new ReadChatCommand(userId, chatRoomId, readMessageId));
    }
}
