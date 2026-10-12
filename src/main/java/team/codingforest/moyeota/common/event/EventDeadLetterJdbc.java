package team.codingforest.moyeota.common.event;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

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

    private static final String DISCARD_FAILED = """
            DELETE FROM event_publication
            WHERE status = 'FAILED'
              AND listener_id LIKE ANY (?)
            """;

    // 바깥 WHERE 의 status 재확인 - 고른 사이 실행이 시작돼 PROCESSING 이 된 행은 건드리지 않음
    private static final String MARK_ABANDONED_FAILED = """
            UPDATE event_publication
               SET status = 'FAILED'
             WHERE id IN (
                   SELECT id
                     FROM event_publication
                    WHERE status = 'PUBLISHED'
                      AND completion_date IS NULL
                      AND publication_date < ?
                    ORDER BY publication_date
                    LIMIT ?)
               AND status = 'PUBLISHED'
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public int moveExhausted(int maxAttempts) {
        return jdbcTemplate.update(MOVE_EXHAUSTED, maxAttempts);
    }

    /**
     * 재발행하지 않는 리스너의 실패 건을 지움
     * @param listenerPrefixes 지울 리스너 id 접두사
     */
    @Override
    @Transactional
    public int discardFailed(List<String> listenerPrefixes) {
        String[] patterns = listenerPrefixes.stream()
                .map(prefix -> prefix + "%")
                .toArray(String[]::new);
        return jdbcTemplate.update(DISCARD_FAILED,
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", patterns)));
    }

    @Override
    @Transactional
    public int markAbandonedFailed(Instant publishedBefore, int limit) {
        return jdbcTemplate.update(MARK_ABANDONED_FAILED, Timestamp.from(publishedBefore), limit);
    }
}