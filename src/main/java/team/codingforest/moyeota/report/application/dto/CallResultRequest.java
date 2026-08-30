package team.codingforest.moyeota.report.application.dto;

import jakarta.validation.constraints.NotNull;

public record CallResultRequest(@NotNull Boolean called) {
}