package team.codingforest.moyeota.chat.room;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.chat.room.dto.ChatRoomResult;
import team.codingforest.moyeota.user.api.CurrentUser;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @GetMapping("/{chatRoomId}")
    public ChatRoomResult getChatRoom(@CurrentUser Long userId,
                                      @PathVariable Long chatRoomId) {
        return chatRoomService.findById(userId, chatRoomId);
    }

}
