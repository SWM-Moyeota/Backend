package team.codingforest.moyeota.matching.api;

import java.time.Instant;

public record MatchingTarget(Long partyId, Instant matchingStartedAt) {
}
