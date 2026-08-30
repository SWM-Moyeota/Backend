package team.codingforest.moyeota.driver.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterFcmTokenRequest(@NotBlank String token) {
}
