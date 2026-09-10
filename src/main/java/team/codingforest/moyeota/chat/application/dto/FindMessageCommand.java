package team.codingforest.moyeota.chat.application.dto;

public record FindMessageCommand(Long userId, Long chatRoomId, Long cursor, int size) {
}
