package team.codingforest.moyeota.user.domain;

import java.time.Instant;

public record IdentityRequest(String id, Long userId, String storeId, String channelKey,
                              Instant createdAt, Instant expiresAt) {}
