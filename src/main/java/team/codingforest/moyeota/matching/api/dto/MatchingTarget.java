package team.codingforest.moyeota.matching.api.dto;

import java.time.Instant;

public record MatchingTarget(Long partyId, Instant matchingStartedAt) {
}
