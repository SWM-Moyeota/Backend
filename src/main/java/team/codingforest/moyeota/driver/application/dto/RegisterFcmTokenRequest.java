package team.codingforest.moyeota.driver.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RegisterFcmTokenRequest(@Schema(description = "기사 앱 FCM registration token") @NotBlank String token) {
}
