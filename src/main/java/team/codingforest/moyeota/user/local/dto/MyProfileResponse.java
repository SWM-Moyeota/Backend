package team.codingforest.moyeota.user.local.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 *  내 프로필. noShowCount·restrictedUntil 은 노쇼 기능을 없앤 뒤 값이 고정이다(늘 0·null).
 *  11-03 출시 앱이 두 필드를 읽으므로 남겨 둔다 - 앱이 필드를 빼고 강제 업데이트된 뒤 지운다
 */
public record MyProfileResponse(
        @Schema(description = "publicId") UUID uuid,
        @Schema(description = "표시 이름(닉네임, 없으면 가린 실명)") String name,
        @Schema(description = "프로필 이미지 URL") String imageUrl,
        @Schema(description = "늘 0. 노쇼 기능이 없어져 쓰지 않는다(출시 앱 호환용으로 키만 남김)", example = "0", deprecated = true) int noShowCount,
        @Schema(description = "늘 null. 노쇼 기능이 없어져 쓰지 않는다(출시 앱 호환용으로 키만 남김)", nullable = true, deprecated = true) Instant restrictedUntil) {
}
