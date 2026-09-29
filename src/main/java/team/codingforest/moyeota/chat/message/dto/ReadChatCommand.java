package team.codingforest.moyeota.chat.message.dto;

public record ReadChatCommand(
        Long userId,
        Long chatRoomId,
        Long lastReadMessageId
) {
}
