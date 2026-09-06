package team.codingforest.moyeota.user.application.dto;

import java.util.UUID;

public record TokenClaims(
        UUID publicId,
        UUID jti
) {
}
