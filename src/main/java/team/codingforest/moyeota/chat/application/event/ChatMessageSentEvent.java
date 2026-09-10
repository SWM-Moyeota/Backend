package team.codingforest.moyeota.chat.application.event;

import team.codingforest.moyeota.chat.application.dto.ChatMessageResult;

public record ChatMessageSentEvent(ChatMessageResult result) {
}
