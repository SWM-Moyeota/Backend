package team.codingforest.moyeota.user.auth.dto;

import java.util.UUID;

public record AuthenticatedUser(
        Long userId,
        UUID publicId
) {
}
