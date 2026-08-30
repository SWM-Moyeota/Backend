package team.codingforest.moyeota.chat.app.dto;

import java.util.List;

public record ChatMessageSlice(
        List<ChatMessageResult> messages,
        Long nextCursor,
        boolean hasNext
) {
}
