package team.codingforest.moyeota.chat.infrastructure;

import java.util.UUID;

public record RoomLeftPayload(UUID publicId, Long chatRoomId) {}