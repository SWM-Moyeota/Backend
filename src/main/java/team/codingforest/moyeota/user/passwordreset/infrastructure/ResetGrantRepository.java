package team.codingforest.moyeota.user.passwordreset.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface ResetGrantRepository extends JpaRepository<ResetGrantEntity, UUID> {
 @Query("select g.userId from ResetGrantEntity g where g.tokenHash = :hash")
 Optional<Long> findOwnerId(@Param("hash") String hash);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select g from ResetGrantEntity g where g.tokenHash = :hash")
 Optional<ResetGrantEntity> lockByTokenHash(@Param("hash") String hash);
 @Modifying(flushAutomatically=true)
 @Query("update ResetGrantEntity g set g.consumedAt = :now where g.userId = :id and g.consumedAt is null")
 int consumeAll(@Param("id") Long id, @Param("now") Instant now);
 @Modifying
 @Query("delete from ResetGrantEntity g where g.expiresAt < :before")
 int deleteExpired(@Param("before") Instant before);
}
