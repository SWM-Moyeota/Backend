package team.codingforest.moyeota.user.api;

import java.util.UUID;

public record MemberSummary(UUID publicId, String nickname, String imageUrl, Long badgeId) {
}
