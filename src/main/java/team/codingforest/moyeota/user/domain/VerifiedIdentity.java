package team.codingforest.moyeota.user.domain;

import java.time.Instant;

/** 개인정보 원문 대신 인증 출처와 시각만 애플리케이션에 노출한다. */
public record VerifiedIdentity(Long userId, String requestId, Instant verifiedAt, Instant completedAt) {}
