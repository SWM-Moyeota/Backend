package team.codingforest.moyeota.chat.domain;

import lombok.Getter;
import team.codingforest.moyeota.chat.domain.enums.ReportReasonType;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;

@Getter
public class ChatReport {
    private final Long id;
    private final Long userId;
    private final Long reportedUserId;
    private final Long chatMessageId;
    private final ReportReasonType reason;
    private final String description;
    private final Instant createdAt;

    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private ChatReport(Long id, Long userId, Long reportedUserId, Long chatMessageId, ReportReasonType reason, String description, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.reportedUserId = reportedUserId;
        this.chatMessageId = chatMessageId;
        this.reason = reason;
        this.description = description;
        this.createdAt = createdAt;
    }

    public static ChatReport create(Long userId, Long reportedUserId, Long chatMessageId,
                                    ReportReasonType reason, String description, Instant createdAt) {
        if (userId.equals(reportedUserId)) {
            throw new ChatException(ChatErrorCode.CHAT_CANNOT_REPORT_SELF);
        }
        if (reason == ReportReasonType.ETC && (description == null || description.isBlank())) {
            throw new ChatException(ChatErrorCode.CHAT_REPORT_DESCRIPTION_REQUIRED);
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ChatException(ChatErrorCode.CHAT_REPORT_DESCRIPTION_TOO_LONG);
        }

        return new ChatReport(null, userId, reportedUserId, chatMessageId, reason, description, createdAt);
    }

    public static ChatReport restore(Long id, Long userId, Long reportedUserId, Long chatMessageId, ReportReasonType reason, String description, Instant createdAt) {
        return new ChatReport(id, userId, reportedUserId, chatMessageId, reason, description, createdAt);
    }
}