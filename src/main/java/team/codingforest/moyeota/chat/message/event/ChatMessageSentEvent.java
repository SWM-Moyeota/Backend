package team.codingforest.moyeota.chat.message.event;

import team.codingforest.moyeota.chat.message.dto.ChatMessageResult;

public record ChatMessageSentEvent(Long senderId, ChatMessageResult result) {
}
