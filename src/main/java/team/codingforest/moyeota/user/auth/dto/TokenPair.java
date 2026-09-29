package team.codingforest.moyeota.user.auth.dto;

import java.time.Instant;

public record TokenPair(
        String access,
        String refresh,
        Instant refreshExpiresAt
) {}
