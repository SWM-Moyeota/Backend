package team.codingforest.moyeota.chat.room.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.room.domain.ChatEventDeadLetters;

@Repository
@RequiredArgsConstructor
public class ChatEventDeadLetterJdbc implements ChatEventDeadLetters {

    private static final String MOVE_EXHAUSTED = """
            WITH moved AS (
                DELETE FROM event_publication
                WHERE status = 'FAILED'
                    AND completion_attempts >= ?
                    AND listener_id LIKE ?
                 RETURNING id, listener_id, event_type, serialized_event,
                          publication_date, completion_attempts, last_resubmission_date
            )
            INSERT INTO chat_event_dead_letter (id, listener_id, event_type, serialized_event,
                                                publication_date, completion_attempts, last_resubmission_date, dead_at)
            SELECT id, listener_id, event_type, serialized_event,
                   publication_date, completion_attempts, last_resubmission_date, now()
            FROM moved
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public int moveExhausted(String listenerIdPrefix, int maxAttempts) {
        return jdbcTemplate.update(MOVE_EXHAUSTED, maxAttempts, listenerIdPrefix + "%");
    }
}
