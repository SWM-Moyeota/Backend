package team.codingforest.moyeota.chat.infrastructure.search;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

public interface SearchOutboxRepository extends JpaRepository<SearchOutboxEntry, Long> {
    List<SearchOutboxEntry> findByNextAttemptAtLessThanEqualOrderByIdAsc(Instant now, Limit limit);

    @Modifying @Transactional
    @Query("update SearchOutboxEntry e set e.attempts = e.attempts + 1, e.nextAttemptAt = :next where e.id = :id")
    void postpone(@Param("id") Long id, @Param("next") Instant next);

    @Modifying @Transactional
    @Query("delete from SearchOutboxEntry e where e.id = :id")
    void acknowledge(@Param("id") Long id);
}
