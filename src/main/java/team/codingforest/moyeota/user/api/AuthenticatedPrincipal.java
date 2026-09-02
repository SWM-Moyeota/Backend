package team.codingforest.moyeota.user.api;

import java.util.UUID;

/**
 * 인증된 요청의 사용자 식별자. userId 는 내부 PK, publicId 는 외부 노출용 UUID v7.
 */
public record AuthenticatedPrincipal(Long userId, UUID publicId) {
}
