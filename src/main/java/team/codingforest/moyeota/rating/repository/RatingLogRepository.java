package team.codingforest.moyeota.rating.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.rating.entity.RatingLog;
import team.codingforest.moyeota.rating.entity.RatingLogId;

import java.util.Optional;

public interface RatingLogRepository extends JpaRepository<RatingLog, RatingLogId> {

    Optional<RatingLog> findByMatchIdAndRaterIdAndRateeId(Long matchId, Long raterId, Long rateeId);

    boolean existsByMatchIdAndRaterIdAndRateeId(Long matchId, Long raterId, Long rateeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ratingLog from RatingLog ratingLog
            where ratingLog.matchId = :matchId
              and ratingLog.raterId = :raterId
              and ratingLog.rateeId = :rateeId
            """)
    Optional<RatingLog> findForUpdate(
            @Param("matchId") Long matchId,
            @Param("raterId") Long raterId,
            @Param("rateeId") Long rateeId
    );
}
