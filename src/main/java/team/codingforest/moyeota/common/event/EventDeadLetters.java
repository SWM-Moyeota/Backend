package team.codingforest.moyeota.common.event;

public interface EventDeadLetters {
    int moveExhausted(int maxAttempts);
}
