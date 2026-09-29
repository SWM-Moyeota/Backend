package team.codingforest.moyeota.chat.message.dto;

public record SearchMessageCommand(
        Long userId,
        Long chatRoomId,
        String keyword,
        Long cursor,
        int size
) {
}
