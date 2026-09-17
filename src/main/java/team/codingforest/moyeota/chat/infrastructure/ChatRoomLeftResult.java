package team.codingforest.moyeota.chat.infrastructure;

import java.util.UUID;

public record ChatRoomLeftResult(Long userId, UUID publicId, Long chatRoomId) {
}