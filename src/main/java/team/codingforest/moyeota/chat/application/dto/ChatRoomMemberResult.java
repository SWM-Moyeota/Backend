package team.codingforest.moyeota.chat.application.dto;

import java.util.UUID;

public record ChatRoomMemberResult(
        UUID publicId,
        String nickname,
        String imageUrl,
        boolean active
) {}