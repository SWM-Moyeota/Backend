package team.codingforest.moyeota.common.event;

import java.time.Duration;
import java.time.Instant;

/** 1분, 2분 간격으로 재시도하고 3회에서 멈춤 */
public class EventRetryPolicy {

    public static final int MAX_ATTEMPTS = 3;
    private static final Duration BASE_DELAY = Duration.ofMinutes(1);

    private EventRetryPolicy() {
    }

    public static boolean isExhausted(int attempts) {
        return attempts >= MAX_ATTEMPTS;
    }

    public static boolean isDue(int attempts, Instant publishedAt, Instant lastResubmittedAt, Instant now) {
        if (isExhausted(attempts)) {
            return false;
        }

        Instant lastTry = lastResubmittedAt == null ? publishedAt : lastResubmittedAt;
        Duration wait = BASE_DELAY.multipliedBy(1L << Math.max(0, attempts - 1));
        return !now.isBefore(lastTry.plus(wait));
    }
}
