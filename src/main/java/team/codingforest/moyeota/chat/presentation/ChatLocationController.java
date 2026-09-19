package team.codingforest.moyeota.chat.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.chat.application.ChatLocationService;
import team.codingforest.moyeota.chat.presentation.dto.LocationRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat-rooms/{chatRoomId}/location")
@RequiredArgsConstructor
public class ChatLocationController {

    private final ChatLocationService chatLocationService;

    /**
     * 위치 공유 토글 ON
     */
    @PostMapping("/sharing")
    @ResponseStatus(HttpStatus.CREATED)
    public void startSharing(
            @CurrentUser Long userId,
            @PathVariable Long chatRoomId
    ) {
        chatLocationService.startSharing(userId, chatRoomId);
    }

    /**
     * 위치 공유 토글 OFF
     */
    @DeleteMapping("/sharing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stopSharing(
            @CurrentUser Long userId,
            @PathVariable Long chatRoomId
    ) {
        chatLocationService.stopSharing(userId, chatRoomId);
    }

    /**
     * 백그라운드 위치 발행. 포그라운드는 STOMP 경로를 쓴다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void share(
            @CurrentUser Long userId,
            @CurrentUser UUID publicId,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody LocationRequest request
    ) {
        chatLocationService.share(userId, publicId, chatRoomId, request.toDomain());
    }
}
