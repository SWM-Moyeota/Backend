package team.codingforest.moyeota.matching.noshow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.matching.noshow.domain.NoShowHistory;

import java.time.Instant;
import java.util.List;

public record MyNoShowListResponse(@Schema(description = "내 노쇼 기록(무효로 돌아간 것 포함). 노쇼로 찍힌 시각 최신순, 최대 20개") List<NoShowItem> noShows) {

    public record NoShowItem(@Schema(description = "방 ID") Long partyId,
                             @Schema(description = "출발지 이름") String departure,
                             @Schema(description = "도착지 이름") String destination,
                             @Schema(description = "약속 시간(방 상세 departureAt). 이용 제한은 여기서부터 센다") Instant meetingAt,
                             @Schema(description = "노쇼로 찍힌 시각 - 누군가 출발을 누른 시각이거나 서버가 방을 자동 정리한 시각") Instant noShowAt,
                             @Schema(description = "운영자가 이의를 받아들여 무효로 돌린 노쇼인지. true 면 노쇼로 치지 않는다") boolean voided,
                             @Schema(description = "이 방에 낸 이의 상태 PENDING(처리 대기) / ACCEPTED(인정) / REJECTED(거절). 내지 않았으면 null", nullable = true) String objectionStatus) {

        static NoShowItem from(NoShowHistory history) {
            return new NoShowItem(history.partyId(), history.departure(), history.destination(), history.meetingAt(), history.noShowAt(),
                    history.isVoided(), history.objectionStatus() == null ? null : history.objectionStatus().name());
        }
    }

    public static MyNoShowListResponse from(List<NoShowHistory> histories) {
        return new MyNoShowListResponse(histories.stream().map(NoShowItem::from).toList());
    }
}
