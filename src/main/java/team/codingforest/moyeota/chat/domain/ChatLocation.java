package team.codingforest.moyeota.chat.domain;

import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Duration;
import java.time.Instant;

public record ChatLocation(double latitude, double longitude, Instant measuredAt) {

    private static final double MAX_LATITUDE = 90.0;
    private static final double MAX_LONGITUDE = 180.0;

    public ChatLocation {
        if (Math.abs(latitude) > MAX_LATITUDE || Math.abs(longitude) > MAX_LONGITUDE) {
            throw new ChatException(ChatErrorCode.CHAT_INVALID_COORDINATE);
        }

        if (measuredAt == null) {
            throw new ChatException(ChatErrorCode.CHAT_INVALID_COORDINATE);
        }
    }
    private static final Duration FRESH = Duration.ofSeconds(30);

    public boolean isFresh(Instant now) {
        return Duration.between(measuredAt, now).compareTo(FRESH) < 0;
    }

    public boolean isNewerThan(ChatLocation other) {
        return other == null || measuredAt.isAfter(other.measuredAt());
    }
}
