package team.codingforest.moyeota.user.application.dto;

import java.util.UUID;

public record AuthenticatedUser(
        Long userId,
        UUID publicId
) {
}
