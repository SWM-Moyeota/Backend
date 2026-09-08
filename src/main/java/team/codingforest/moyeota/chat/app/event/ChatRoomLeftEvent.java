package team.codingforest.moyeota.chat.app.event;

import java.util.UUID;

public record ChatRoomLeftEvent(Long userId, UUID publicId, Long chatRoomId) {}
