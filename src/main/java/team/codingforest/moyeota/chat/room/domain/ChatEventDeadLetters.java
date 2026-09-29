package team.codingforest.moyeota.chat.room.domain;

public interface ChatEventDeadLetters {
    int moveExhausted(String listenerIdPrefix, int maxAttempts);
}
