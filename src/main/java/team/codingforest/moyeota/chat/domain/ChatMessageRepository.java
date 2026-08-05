package team.codingforest.moyeota.chat.domain;

import java.util.List;

public interface ChatMessageRepository {
    ChatMessage save(ChatMessage chatMessage);
    List<ChatMessage> findBefore(Long chatRoomId, Long cursor, int size);
    List<ChatMessage> findAfter(Long chatRoomId, Long cursor, int size);
}
