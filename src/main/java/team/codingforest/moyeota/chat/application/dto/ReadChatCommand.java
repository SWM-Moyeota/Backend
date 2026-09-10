package team.codingforest.moyeota.chat.application.dto;

public record ReadChatCommand(
        Long userId,
        Long chatRoomId,
        Long lastReadMessageId
) {
}
