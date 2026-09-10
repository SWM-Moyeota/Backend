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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.chat.application.ChatRoomService;
import team.codingforest.moyeota.chat.application.dto.ChatRoomResult;
import team.codingforest.moyeota.chat.application.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.presentation.dto.ChatRoomRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @GetMapping("/{chatRoomId}")
    public ChatRoomResult getChatRoom(@CurrentUser Long userId,
                                      @PathVariable Long chatRoomId) {
        return chatRoomService.findById(chatRoomId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatRoomResult createChatRoom(
            @CurrentUser Long userId,
            @Valid @RequestBody ChatRoomRequest request) {
        return chatRoomService.createRoom(new CreateChatRoomCommand(request.partyId(), request.departure(), request.destination()));
    }

    @DeleteMapping("/{chatRoomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChatRoom(
            @CurrentUser Long userId,
            @PathVariable Long chatRoomId) {
        chatRoomService.close(chatRoomId);
    }
}
