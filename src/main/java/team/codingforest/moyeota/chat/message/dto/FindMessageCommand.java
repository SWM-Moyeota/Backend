package team.codingforest.moyeota.chat.message.dto;

public record FindMessageCommand(Long userId, Long chatRoomId, Long cursor, int size) {
}
