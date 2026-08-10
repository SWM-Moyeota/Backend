package team.codingforest.moyeota.chat.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.chat.app.ChatRoomService;
import team.codingforest.moyeota.chat.app.dto.CreateChatRoomCommand;
import team.codingforest.moyeota.chat.app.dto.ChatRoomResult;
import team.codingforest.moyeota.chat.presentation.dto.ChatRoomRequest;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @GetMapping("/{chatRoomId}")
    public ChatRoomResult getChatRoom(@PathVariable Long chatRoomId) {
        return chatRoomService.findById(chatRoomId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatRoomResult createChatRoom(@Valid @RequestBody ChatRoomRequest request) {
        return chatRoomService.createRoom(new CreateChatRoomCommand(request.partyId(), request.departure(), request.destination()));
    }

    @DeleteMapping("/{chatRoomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChatRoom(@PathVariable Long chatRoomId) {
        chatRoomService.close(chatRoomId);
    }
}
