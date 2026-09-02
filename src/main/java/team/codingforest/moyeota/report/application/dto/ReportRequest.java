package team.codingforest.moyeota.report.application.dto;

import jakarta.validation.constraints.NotNull;

public record ReportRequest(Long partyId, Double latitude, Double longitude) {
}