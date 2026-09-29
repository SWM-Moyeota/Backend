package team.codingforest.moyeota.chat.member.infrastructure;

import java.util.UUID;

public record RoomLeftPayload(UUID publicId, Long chatRoomId) {}