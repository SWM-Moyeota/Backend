package team.codingforest.moyeota.chat.infra;

import team.codingforest.moyeota.chat.app.event.ChatRoomLeftEvent;

public record ChatRoomLeftResult(Long userId, Long chatRoomId) {

    public static ChatRoomLeftResult from(ChatRoomLeftEvent event) {
        return new ChatRoomLeftResult(event.userId(), event.chatRoomId());
    }
}
