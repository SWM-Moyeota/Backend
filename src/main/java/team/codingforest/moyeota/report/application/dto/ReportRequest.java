package team.codingforest.moyeota.report.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
public record ReportRequest(@Schema(description = "운행 중인 방", example = "12") Long partyId,
                            @Schema(description = "신고 시점 GPS", example = "37.4979") Double latitude, @Schema(example = "127.0276") Double longitude) {
}