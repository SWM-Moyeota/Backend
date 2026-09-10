package team.codingforest.moyeota.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record NicknameCheckRequest(@Schema(example = "길동이") @NotBlank String nickname) {
}
