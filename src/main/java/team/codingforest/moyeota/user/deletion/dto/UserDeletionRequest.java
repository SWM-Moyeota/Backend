package team.codingforest.moyeota.user.deletion.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 회원 탈퇴 요청
 *
 * @param password 본인 확인용 현재 비밀번호
 */
public record UserDeletionRequest(@NotBlank String password) {
}
