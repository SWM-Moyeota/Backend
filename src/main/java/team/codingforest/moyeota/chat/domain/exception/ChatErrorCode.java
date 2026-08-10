package team.codingforest.moyeota.chat.domain.exception;

import lombok.Getter;

@Getter
public enum ChatErrorCode {
    CHAT_ROOM_NOT_FOUND("채팅방이 존재하지 않습니다."),
    CHAT_ROOM_INVALID_STATUS("채팅방 상태를 변경할 수 없습니다."),
    CHAT_ROOM_CLOSED("종료된 채팅방입니다."),
    CHAT_ROOM_ALREADY_EXISTS("이미 존재하는 채팅방입니다."),
    CHAT_NOT_PARTICIPANT("채팅방 참여자가 아닙니다."),
    CHAT_MESSAGE_NOT_FOUND("메시지가 존재하지 않습니다."),
    CHAT_NOT_MESSAGE_OWNER("본인이 보낸 메시지만 삭제할 수 있습니다."),
    CHAT_MESSAGE_ALREADY_DELETED("이미 삭제된 메시지입니다."),
    CHAT_MESSAGE_TOO_LONG("메시지는 1000자를 넘을 수 없습니다."),
    CHAT_MESSAGE_BLANK("메시지 내용이 비어 있습니다."),
    CHAT_INVALID_PAGE_SIZE("조회 개수가 허용 범위를 벗어났습니다."),
    CHAT_INVALID_CURSOR("커서 값이 올바르지 않습니다.");

    private final String message;

    ChatErrorCode(String message) {
        this.message = message;
    }

}
