package team.codingforest.moyeota.chat.domain.search;

import java.time.Instant;
import team.codingforest.moyeota.chat.domain.exception.ChatException;
import static team.codingforest.moyeota.chat.domain.exception.ChatErrorCode.*;

public record MessageSearchQuery(Long roomId, String keyword, Long senderId,
                                 Instant from, Instant until, Long cursor, int size) {
    public MessageSearchQuery {
        if (roomId == null || roomId < 1) throw new ChatException(CHAT_ROOM_NOT_FOUND);
        if (keyword == null || keyword.strip().length() < 2 || keyword.strip().length() > 100)
            throw new ChatException(CHAT_INVALID_KEYWORD);
        keyword = keyword.strip();
        if (size < 1 || size > 100) throw new ChatException(CHAT_INVALID_PAGE_SIZE);
        if (cursor != null && cursor < 1) throw new ChatException(CHAT_INVALID_CURSOR);
        if ((senderId != null && senderId < 1) || (from != null && until != null && !from.isBefore(until)))
            throw new ChatException(CHAT_INVALID_SEARCH_FILTER);
    }
}
