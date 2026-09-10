package team.codingforest.moyeota.chat.application.dto;

import java.util.List;

public record ChatMessageSlice(
        List<ChatMessageResult> messages,
        Long nextCursor,
        boolean hasNext
) {
}
