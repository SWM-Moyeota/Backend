package team.codingforest.moyeota.chat.application.dto;

import team.codingforest.moyeota.chat.domain.enums.ReportReasonType;

public record ReportChatCommand (
        Long chatRoomId,
        Long userId,
        Long chatMessageId,
        ReportReasonType reason,
        String description
) {
}
