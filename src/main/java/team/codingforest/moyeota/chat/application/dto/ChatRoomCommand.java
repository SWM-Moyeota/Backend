package team.codingforest.moyeota.chat.application.dto;

import java.util.UUID;

public record ChatRoomCommand(
        Long chatRoomId,
        Long userId,
        UUID publicId
) {
}
