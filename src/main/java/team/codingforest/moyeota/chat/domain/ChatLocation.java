package team.codingforest.moyeota.chat.domain;

import java.time.Duration;
import java.time.Instant;

public record ChatLocation(double latitude, double longitude, Instant measuredAt) {

    private static final Duration FRESH = Duration.ofMinutes(2);

    public boolean isFresh(Instant now) {
        return Duration.between(measuredAt, now).compareTo(FRESH) < 0;
    }

    public boolean isNewerThan(ChatLocation other) {
        return other == null || measuredAt.isAfter(other.measuredAt());
    }
}
