package team.codingforest.moyeota.rating.dto;

import team.codingforest.moyeota.rating.entity.RatingType;
import team.codingforest.moyeota.rating.entity.UserRating;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserRatingResponse(
        Long userId,
        RatingType ratingType,
        int ratingSum,
        int totalRatings,
        BigDecimal avgRating,
        LocalDateTime updatedAt
) {
    public static UserRatingResponse from(UserRating userRating) {
        return new UserRatingResponse(
                userRating.getUserId(), userRating.getRatingType(), userRating.getRatingSum(),
                userRating.getTotalRatings(), userRating.getAvgRating(), userRating.getUpdatedAt()
        );
    }

    public static UserRatingResponse empty(Long userId, RatingType ratingType) {
        return new UserRatingResponse(userId, ratingType, 0, 0, BigDecimal.ZERO, null);
    }
}
