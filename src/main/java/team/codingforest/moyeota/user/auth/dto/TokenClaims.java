package team.codingforest.moyeota.user.auth.dto;

import java.util.UUID;

public record TokenClaims(
        UUID publicId,
        UUID jti
) {
}
