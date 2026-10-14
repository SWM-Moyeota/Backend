package team.codingforest.moyeota.user.deletion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 회원 탈퇴 요청
 *
 * @param password 본인 확인용 현재 비밀번호
 */
public record UserDeletionRequest(@Schema(description = "본인 확인용 현재 비밀번호", example = "Passw0rd!") @NotBlank(message = "비밀번호는 필수입니다.") String password) {
}
