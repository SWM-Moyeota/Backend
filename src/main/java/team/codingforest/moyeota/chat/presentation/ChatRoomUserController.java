package team.codingforest.moyeota.chat.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.chat.application.ChatRoomUserService;
import team.codingforest.moyeota.chat.application.dto.ChatRoomCommand;
import team.codingforest.moyeota.chat.application.dto.ChatRoomMemberResult;
import team.codingforest.moyeota.chat.application.dto.ChatRoomUserResult;
import team.codingforest.moyeota.chat.application.dto.ReadChatCommand;
import team.codingforest.moyeota.user.api.CurrentUser;

import java.util.List;
import java.util.UUID;

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
            @CurrentUser UUID publicId,
            @PathVariable Long chatRoomId
    ) {
        chatRoomUserService.join(new ChatRoomCommand(chatRoomId, userId, publicId));
    }

    @DeleteMapping("/{chatRoomId}/users")
    @ResponseStatus(HttpStatus.OK)
    public void leave(
            @CurrentUser Long userId,
            @CurrentUser UUID publicId,
            @PathVariable Long chatRoomId
    ) {
        chatRoomUserService.leave(new ChatRoomCommand(chatRoomId, userId, publicId));
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

    @GetMapping("/{chatRoomId}/users")
    public List<ChatRoomMemberResult> getMembers(
            @CurrentUser Long userId,
            @PathVariable Long chatRoomId
    ) {
        return chatRoomUserService.findMembers(userId, chatRoomId);
    }
}
