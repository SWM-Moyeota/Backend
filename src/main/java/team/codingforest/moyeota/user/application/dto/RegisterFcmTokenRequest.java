package team.codingforest.moyeota.user.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterFcmTokenRequest(@NotBlank String token) {
}
