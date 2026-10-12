package team.codingforest.moyeota.user.deletion.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface UserDeletionRepository extends JpaRepository<UserDeletionEntity, String> {
    boolean existsByPhoneHashAndDeletedAtAfter(String phoneHash, Instant since);

    @Modifying
    @Query("delete from UserDeletionEntity d where d.deletedAt < :before")
    int deleteRecordedBefore(@Param("before") Instant before);
}
