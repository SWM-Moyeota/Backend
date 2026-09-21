package team.codingforest.moyeota.chat.infrastructure;

import team.codingforest.moyeota.chat.domain.enums.MemberChangeType;

import java.util.UUID;

public record MemberChangedPayload(
        Long chatRoomId,
        UUID publicId,
        String nickname,
        String imageUrl,
        MemberChangeType type
) {
}
