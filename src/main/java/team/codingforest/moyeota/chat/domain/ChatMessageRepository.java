package team.codingforest.moyeota.chat.domain;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository {
    Optional<ChatMessage> findById(Long id);

    ChatMessage save(ChatMessage chatMessage);

    List<ChatMessage> findBefore(Long chatRoomId, Long cursor, int size);

    List<ChatMessage> findAfter(Long chatRoomId, Long cursor, int size);

    List<ChatMessage> search(Long chatRoomId, String keyword, Long cursor, int size);
}
