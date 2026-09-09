package team.codingforest.moyeota.chat.infra;

import java.util.UUID;

public record RoomLeftPayload(UUID publicId, Long chatRoomId) {}