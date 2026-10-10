package team.codingforest.moyeota.common.event;

import java.time.Instant;
import java.util.List;

public interface EventDeadLetters {
    int moveExhausted(int maxAttempts);

    int discardFailed(List<String> listenerPrefixes);

    /**
     * 실행기에 들어가지 못하고 PUBLISHED 로 남은 건을 FAILED 로 바꿔 재발행 대상에 올림
     *
     * @param publishedBefore 이 시각보다 먼저 발행된 것만
     * @param limit 한 번에 바꿀 최대 건수
     * @return 바꾼 건수
     */
    int markAbandonedFailed(Instant publishedBefore, int limit);
}
