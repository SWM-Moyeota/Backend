package team.codingforest.moyeota.chat.infrastructure;

import java.time.Instant;
import java.util.UUID;

public record ChatLocationPayload(
        Long chatRoomId,
        UUID publicId,
        double latitude,
        double longitude,
        Instant measuredAt
) {
}