package team.codingforest.moyeota.rating.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.rating.entity.RatingType;
import team.codingforest.moyeota.rating.entity.UserRating;
import team.codingforest.moyeota.rating.entity.UserRatingId;

import java.util.Optional;

public interface UserRatingRepository extends JpaRepository<UserRating, UserRatingId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select userRating from UserRating userRating
            where userRating.userId = :userId
              and userRating.ratingType = :ratingType
            """)
    Optional<UserRating> findForUpdate(
            @Param("userId") Long userId,
            @Param("ratingType") RatingType ratingType
    );
}
