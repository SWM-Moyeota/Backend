package team.codingforest.moyeota.chat.room.application;

import java.time.Duration;
import java.time.Instant;

public class ChatEventRetryPolicy {

    public static final int MAX_ATTEMPTS = 5;
    private static final Duration BASE_DELAY = Duration.ofMinutes(1);

    private ChatEventRetryPolicy() {
    }

    public static boolean isExhausted(int attempts) {
        return attempts >= MAX_ATTEMPTS;
    }

    public static boolean isDue(int attempts, Instant publishedAt, Instant lastResubmittedAt, Instant now) {
        if (isExhausted(attempts)) {
            return false;
        }

        Instant lastTry = lastResubmittedAt == null ? publishedAt: lastResubmittedAt;
        Duration wait = BASE_DELAY.multipliedBy(1L << Math.max(0, attempts - 1));
        return !now.isBefore(lastTry.plus(wait));
    }
}
