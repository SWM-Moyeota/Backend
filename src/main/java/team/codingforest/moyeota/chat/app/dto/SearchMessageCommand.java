package team.codingforest.moyeota.chat.app.dto;

public record SearchMessageCommand(
        Long userId,
        Long chatRoomId,
        String keyword,
        Long cursor,
        int size
) {
}
