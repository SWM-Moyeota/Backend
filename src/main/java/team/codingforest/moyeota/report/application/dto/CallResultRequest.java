package team.codingforest.moyeota.report.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CallResultRequest(@Schema(description = "112 와 실제 통화했는지", example = "true") @NotNull Boolean called) {
}