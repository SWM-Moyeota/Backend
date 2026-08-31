package team.codingforest.moyeota.chat.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ChatErrorCode {
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방이 존재하지 않습니다."),
    CHAT_ROOM_INVALID_STATUS(HttpStatus.CONFLICT, "채팅방 상태를 변경할 수 없습니다."),
    CHAT_ROOM_CLOSED(HttpStatus.CONFLICT, "종료된 채팅방입니다."),
    CHAT_ROOM_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 채팅방입니다."),
    CHAT_ROOM_ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여 중인 채팅방입니다."),
    CHAT_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "채팅방 참여자가 아닙니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "메시지가 존재하지 않습니다."),
    CHAT_NOT_MESSAGE_OWNER(HttpStatus.FORBIDDEN, "본인이 보낸 메시지만 삭제할 수 있습니다."),
    CHAT_MESSAGE_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 메시지입니다."),
    CHAT_MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "메시지는 1000자를 넘을 수 없습니다."),
    CHAT_MESSAGE_BLANK(HttpStatus.BAD_REQUEST, "메시지 내용이 비어 있습니다."),
    CHAT_INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "조회 개수가 허용 범위를 벗어났습니다."),
    CHAT_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "커서 값이 올바르지 않습니다."),
    CHAT_INVALID_KEYWORD(HttpStatus.BAD_REQUEST, "검색어는 2글자 이상이어야 합니다."),
    CHAT_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."),
    CHAT_INVALID_DESTINATION(HttpStatus.FORBIDDEN, "허용되지 않은 경로입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ChatErrorCode(HttpStatus status, String message) {
        this.httpStatus = status;
        this.message = message;
    }

}
