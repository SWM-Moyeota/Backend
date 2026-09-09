package team.codingforest.moyeota.chat.app.dto;

import java.util.UUID;

public record SendMessageCommand(
        Long chatRoomId,
        Long userId,
        UUID publicId,
        String content
) {
}
