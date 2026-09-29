package team.codingforest.moyeota.chat.location.dto;

import team.codingforest.moyeota.chat.location.domain.ChatLocation;
import team.codingforest.moyeota.chat.member.domain.ChatMember;

import java.time.Instant;
import java.util.UUID;

public record ChatLocationResult(
        UUID publicId,
        String nickname,
        String imageUrl,
        double latitude,
        double longitude,
        Instant measuredAt
) {
    public static ChatLocationResult of(ChatMember member, ChatLocation location) {
        return new ChatLocationResult(
                member.publicId(),
                member.nickname(),
                member.imageUrl(),
                location.latitude(),
                location.longitude(),
                location.measuredAt()
        );
    }
}
