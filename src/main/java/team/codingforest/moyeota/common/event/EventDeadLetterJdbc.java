package team.codingforest.moyeota.common.event;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class EventDeadLetterJdbc implements EventDeadLetters {

    private static final String MOVE_EXHAUSTED = """
            WITH moved AS (
                DELETE FROM event_publication
                WHERE status = 'FAILED'
                    AND completion_attempts >= ?
                RETURNING id, listener_id, event_type, serialized_event,
                          publication_date, completion_attempts, last_resubmission_date
            )
            INSERT INTO event_dead_letter (id, listener_id, event_type, serialized_event,
                                           publication_date, completion_attempts, last_resubmission_date, dead_at)
            SELECT id, listener_id, event_type, serialized_event,
                   publication_date, completion_attempts, last_resubmission_date, now()
            FROM moved
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public int moveExhausted(int maxAttempts) {
        return jdbcTemplate.update(MOVE_EXHAUSTED, maxAttempts);
    }
}
