package team.codingforest.moyeota.user.api;

import java.util.UUID;

public record AuthenticatedPrincipal(Long userId, UUID publicId) {
}
