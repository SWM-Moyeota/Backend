package team.codingforest.moyeota.matching.noshow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionSummary;

import java.time.Instant;
import java.util.List;

public record ObjectionListResponse(@Schema(description = "이의 신청 목록. 신청 시각 오래된 순, 최대 100개") List<ObjectionItem> objections) {

    public record ObjectionItem(@Schema(description = "이의 신청 ID - 인정·거절 경로에 쓴다") Long objectionId,
                                @Schema(description = "신청한 사용자의 내부 번호(users.id)") Long userId,
                                @Schema(description = "노쇼가 난 방 ID") Long partyId,
                                @Schema(description = "방의 출발지 이름") String departure,
                                @Schema(description = "방의 도착지 이름") String destination,
                                @Schema(description = "방의 약속 시간") Instant meetingAt,
                                @Schema(description = "이의 사유") String reason,
                                @Schema(description = "PENDING / ACCEPTED / REJECTED") String status,
                                @Schema(description = "신청 시각") Instant createdAt,
                                @Schema(description = "처리 시각. 대기 중이면 null", nullable = true) Instant resolvedAt) {

        static ObjectionItem from(ObjectionSummary s) {
            return new ObjectionItem(s.id(), s.userId(), s.partyId(), s.departure(), s.destination(), s.meetingAt(),
                    s.reason(), s.status().name(), s.createdAt(), s.resolvedAt());
        }
    }

    public static ObjectionListResponse from(List<ObjectionSummary> summaries) {
        return new ObjectionListResponse(summaries.stream().map(ObjectionItem::from).toList());
    }
}
