package team.codingforest.moyeota.chat.app.dto;

public record FindMessageCommand(Long userId, Long chatRoomId, Long cursor, int size) {
}
