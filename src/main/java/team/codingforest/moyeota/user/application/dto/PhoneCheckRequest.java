package team.codingforest.moyeota.user.application.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneCheckRequest(@NotBlank String phoneNumber) {
}