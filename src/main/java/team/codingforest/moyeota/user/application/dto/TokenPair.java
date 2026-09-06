package team.codingforest.moyeota.user.application.dto;

import java.time.Instant;

public record TokenPair(
        String access,
        String refresh,
        Instant refreshExpiresAt
) {}
