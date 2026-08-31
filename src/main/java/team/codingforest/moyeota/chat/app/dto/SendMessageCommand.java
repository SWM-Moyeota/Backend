package team.codingforest.moyeota.chat.app.dto;

public record SendMessageCommand(
        Long chatRoomId,
        Long userId,
        String content
) {
}
