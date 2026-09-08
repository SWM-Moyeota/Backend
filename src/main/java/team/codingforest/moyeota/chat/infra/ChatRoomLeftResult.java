package team.codingforest.moyeota.chat.infra;

import team.codingforest.moyeota.chat.app.event.ChatRoomLeftEvent;

import java.util.UUID;

public record ChatRoomLeftResult(Long userId, UUID publicId, Long chatRoomId) {

    public static ChatRoomLeftResult from(ChatRoomLeftEvent event) {
        return new ChatRoomLeftResult(event.userId(), event.publicId(), event.chatRoomId());
    }
}