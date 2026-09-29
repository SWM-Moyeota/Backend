package team.codingforest.moyeota.chat.member.infrastructure;

import team.codingforest.moyeota.chat.member.domain.enums.MemberChangeType;

import java.util.UUID;

public record MemberChangedPayload(
        Long chatRoomId,
        UUID publicId,
        String nickname,
        String imageUrl,
        MemberChangeType type
) {
}
