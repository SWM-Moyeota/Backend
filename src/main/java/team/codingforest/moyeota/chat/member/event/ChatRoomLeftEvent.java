package team.codingforest.moyeota.chat.member.event;

import java.util.UUID;

public record ChatRoomLeftEvent(Long userId, UUID publicId, Long chatRoomId) {}
