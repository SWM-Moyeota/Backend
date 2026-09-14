package team.codingforest.moyeota.chat.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReadMessageRequest(
        @NotNull(message = "읽은 메시지 ID는 필수입니다")
        @Positive(message = "읽은 메시지 ID는 1 이상이어야 합니다")
        Long lastReadMessageId
) {
}
