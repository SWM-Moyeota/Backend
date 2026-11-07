package team.codingforest.moyeota.socket.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import team.codingforest.moyeota.socket.auth.ChatPrincipal;
import team.codingforest.moyeota.socket.common.SocketErrorCode;
import team.codingforest.moyeota.socket.common.SocketException;

/**
 * 연결·구독을 허용할지 API /internal/chat 에 물음
 * API 가 응답하지 못하면 앱이 로그아웃하지 않고 다시 시도하도록 SERVER_UNAVAILABLE 로 바꿈
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatApiClient {
    private static final String TOKEN_EXPIRED_CODE = "USER002";   // API UserErrorCode.TOKEN_EXPIRED

    private final RestClient chatApiRestClient;

    /**
     * 앱이 CONNECT 에 실은 Authorization 헤더를 그대로 넘겨 토큰 주인을 받음
     *
     * @param authorization 앱이 보낸 Authorization 헤더 값
     * @return 연결에 붙일 Principal
     */
    public ChatPrincipal principal(String authorization) {
        ChatPrincipal principal;

        try {
            principal = chatApiRestClient.get()
                    .uri("/internal/chat/principal")
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .retrieve()
                    .body(ChatPrincipal.class);
        } catch (HttpClientErrorException e) {
            if (HttpStatus.UNAUTHORIZED.equals(e.getStatusCode())) {
                throw new SocketException(isExpired(e) ? SocketErrorCode.TOKEN_EXPIRED : SocketErrorCode.TOKEN_INVALID);
            }
            throw unavailable("principal", e.toString());
        } catch (RestClientException e) {
            throw unavailable("principal", e.toString());
        }

        if (principal == null) {
            throw unavailable("principal", "empty body");
        }

        return principal;
    }

    /**
     * 사용자가 그 방에 남아 있는지 확인함, 아니면 NOT_PARTICIPANT 로 거절함
     *
     * @param chatRoomId 구독하려는 채팅방 id
     * @param userId     구독하는 사용자 id
     */
    public void validateParticipant(Long chatRoomId, Long userId) {
        try {
            chatApiRestClient.get()
                    .uri("/internal/chat/chat-rooms/{chatRoomId}/users/{userId}", chatRoomId, userId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Forbidden e) {
            throw new SocketException(SocketErrorCode.NOT_PARTICIPANT);
        } catch (RestClientException e) {
            throw unavailable("participant", e.toString());
        }
    }

    // 본문을 못 읽으면 만료로 보지 않음 - 만료가 아닌데 refresh 를 시키면 앱이 같은 실패를 되풀이함
    private boolean isExpired(HttpClientErrorException e) {
        try {
            ApiErrorResponse body = e.getResponseBodyAs(ApiErrorResponse.class);
            return body != null && TOKEN_EXPIRED_CODE.equals(body.code());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    // 토큰은 로그에 남기지 않음
    private SocketException unavailable(String call, String cause) {
        log.warn("채팅 API 호출 실패 call={}, cause={}", call, cause);
        return new SocketException(SocketErrorCode.SERVER_UNAVAILABLE);
    }

    record ApiErrorResponse(String code, String message) {
    }
}
