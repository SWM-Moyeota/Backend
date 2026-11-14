package team.codingforest.moyeota.chat.common.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.chat.member.application.ChatRoomUserService;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.api.CurrentUser;

import java.util.UUID;

/**
 * 소켓 서버가 STOMP 연결·구독을 허용할지 묻는 내부 API
 * 앱이 부르면 안 되므로 ALB 에서 /internal/* 을 막고 소켓 서버는 API 사설 IP 로 바로 부름
 */
@RestController
@RequestMapping("/internal/chat")
@RequiredArgsConstructor
public class ChatSocketInternalController {
    private final ChatRoomUserService chatRoomUserService;

    /**
     * CONNECT 때 받은 토큰의 주인을 알려줌, 토큰 검증은 JwtAuthenticationFilter 가 함
     *
     * @param userId   토큰 주인의 내부 id
     * @param publicId 토큰 주인의 공개 id
     * @return 소켓 서버가 Principal 로 쓸 사용자 정보
     */
    @GetMapping("/principal")
    public AuthenticatedPrincipal principal(@CurrentUser Long userId, @CurrentUser UUID publicId) {
        return new AuthenticatedPrincipal(userId, publicId);
    }

    /**
     * SUBSCRIBE 전에 사용자가 그 방에 남아 있는지 확인함, 아니면 CHAT_NOT_PARTICIPANT 로 403
     *
     * @param chatRoomId 구독하려는 채팅방 id
     * @param userId     구독하는 사용자 id
     */
    @GetMapping("/chat-rooms/{chatRoomId}/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void validateParticipant(@PathVariable Long chatRoomId, @PathVariable Long userId) {
        chatRoomUserService.validateParticipant(userId, chatRoomId);
    }
}
