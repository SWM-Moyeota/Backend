package team.codingforest.moyeota.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PhoneCheckRequest(@Schema(example = "010-1234-5678") @NotBlank String phoneNumber) {
}