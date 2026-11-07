package team.codingforest.moyeota.user.local.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 *  내 프로필. 가입 응답(UserResponse)과 같은 필드에 노쇼 제재 상태를 더한다
 */
public record MyProfileResponse(
        @Schema(description = "publicId") UUID uuid,
        @Schema(description = "표시 이름(닉네임, 없으면 가린 실명)") String name,
        @Schema(description = "프로필 이미지 URL") String imageUrl,
        @Schema(description = "지금 노쇼 횟수. 노쇼는 건마다 그 방의 약속 시간부터 30일이 지나면 빠지고, 이의가 인정돼 무효가 된 노쇼는 세지 않는다", example = "1") int noShowCount,
        @Schema(description = "이 시각까지 방 만들기·참여 불가(ISO-8601). 제한이 없으면 null", example = "2026-11-04T08:30:00Z", nullable = true) Instant restrictedUntil) {
}
