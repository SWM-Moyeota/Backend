package team.codingforest.moyeota.user.deletion.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 탈퇴 요청
 *
 * @param password 본인 확인용 현재 비밀번호. 아이디·비밀번호로 가입한 회원만 필수이고 소셜 회원은 보내지 않는다
 */
public record UserDeletionRequest(@Schema(description = "본인 확인용 현재 비밀번호. 아이디·비밀번호 회원은 필수, 소셜 회원은 생략", example = "Passw0rd!", nullable = true) String password) {
}
