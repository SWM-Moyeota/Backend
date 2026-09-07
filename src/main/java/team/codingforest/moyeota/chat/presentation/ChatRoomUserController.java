package team.codingforest.moyeota.chat.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.chat.app.ChatRoomUserService;
import team.codingforest.moyeota.chat.app.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.app.dto.ChatRoomUserResult;
import team.codingforest.moyeota.chat.app.dto.ReadChatCommand;
import team.codingforest.moyeota.user.api.CurrentUser;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomUserController {
    private final ChatRoomUserService chatRoomUserService;

    @GetMapping("/me")
    public List<ChatRoomUserResult> getMyActiveRooms(@CurrentUser Long userId) {
        return chatRoomUserService.findMyActiveRooms(userId);
    }

    @PostMapping("/{chatRoomId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public void join(
            @CurrentUser Long userId,
            @PathVariable Long chatRoomId
    ) {
        chatRoomUserService.join(new ChatRoomCommand(chatRoomId, userId));
    }

    @DeleteMapping("/{chatRoomId}/users")
    @ResponseStatus(HttpStatus.OK)
    public void leave(
            @CurrentUser Long userId,
            @PathVariable Long chatRoomId
    ) {
        chatRoomUserService.leave(new ChatRoomCommand(chatRoomId, userId));
    }

    @PostMapping("/{chatRoomId}/users/read/{readMessageId}")
    @ResponseStatus(HttpStatus.OK)
    public void readChatRoom(
            @CurrentUser Long userId,
            @PathVariable Long chatRoomId,
            @PathVariable Long readMessageId
    ) {
        chatRoomUserService.read(new ReadChatCommand(userId, chatRoomId, readMessageId));
    }
}
