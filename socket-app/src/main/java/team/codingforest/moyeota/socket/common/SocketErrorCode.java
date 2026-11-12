package team.codingforest.moyeota.socket.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * STOMP ERROR 프레임 message 헤더에 이름 그대로 싣는 고정 코드, 앱이 이 이름으로 분기하므로 바꾸면 안 됨
 */
@Getter
@RequiredArgsConstructor
public enum SocketErrorCode {
    TOKEN_EXPIRED("토큰이 만료되었습니다."),               // 앱이 refresh 뒤 다시 연결
    TOKEN_INVALID("유효하지 않은 토큰입니다."),             // refresh 하지 않고 다시 로그인
    SERVER_UNAVAILABLE("잠시 후 다시 연결해 주세요."),       // API 가 응답하지 못함, 같은 토큰으로 다시 시도
    NOT_PARTICIPANT("참여하지 않은 채팅방입니다."),
    INVALID_DESTINATION("잘못된 경로입니다."),
    SEND_NOT_SUPPORTED("메시지 전송은 REST API 를 사용해 주세요.");

    private final String message;
}
