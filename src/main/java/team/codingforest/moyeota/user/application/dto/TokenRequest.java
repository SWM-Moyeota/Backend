package team.codingforest.moyeota.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
        @Schema(description = "로그인/재발급 때 받은 refreshToken") @NotBlank String refreshToken
) {}