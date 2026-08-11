package team.codingforest.moyeota.chat.app.dto;

public record ReadChatCommand(
        Long userId,
        Long chatRoomId,
        Long lastReadMessageId
) {
}
