package team.codingforest.moyeota.common.event;

import java.util.List;

public interface EventDeadLetters {
    int moveExhausted(int maxAttempts);

    int discardFailed(List<String> listenerPrefixes);
}