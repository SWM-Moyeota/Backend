package team.codingforest.moyeota.dispatch.application.dto;

import jakarta.validation.constraints.NotNull;

public record LocationReportRequest(@NotNull Double latitude, @NotNull Double longitude) {
}
