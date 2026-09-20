package team.codingforest.moyeota.chat.application.dto;

import java.util.List;

public record MessageSearchPage(List<Item> messages, Long nextCursor, boolean hasNext) {
    public record Item(ChatMessageResult message, String highlight) {}
}
