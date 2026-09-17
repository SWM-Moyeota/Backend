package team.codingforest.moyeota.chat.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import team.codingforest.moyeota.chat.domain.ChatReport;
import team.codingforest.moyeota.chat.domain.enums.ReportReasonType;

import java.time.Instant;


@Entity
@Table(name = "chat_report",
    uniqueConstraints = {
            @UniqueConstraint(name = "uk_chat_report_user_message", columnNames = {"user_id", "chat_message_id"})
    })
public class ChatReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatRoomId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long reportedUserId;

    @Column(nullable = false)
    private Long chatMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReasonType reason;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Instant createdAt;


    protected ChatReportEntity() {

    }

    private ChatReportEntity(Long id, Long chatRoomId, Long userId, Long reportedUserId, Long chatMessageId, ReportReasonType reason, String description, Instant createdAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.reportedUserId = reportedUserId;
        this.chatMessageId = chatMessageId;
        this.reason = reason;
        this.description = description;
        this.createdAt = createdAt;
    }

    public static ChatReportEntity from(ChatReport chatReport) {
        return new ChatReportEntity(
                chatReport.getId(),
                chatReport.getChatRoomId(),
                chatReport.getUserId(),
                chatReport.getReportedUserId(),
                chatReport.getChatMessageId(),
                chatReport.getReason(),
                chatReport.getDescription(),
                chatReport.getCreatedAt());
    }

    public ChatReport toDomain() {
        return ChatReport.restore(id, chatRoomId,
                userId, reportedUserId,
                chatMessageId, reason,
                description, createdAt);
    }
}
