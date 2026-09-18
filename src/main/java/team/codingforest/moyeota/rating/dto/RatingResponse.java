package team.codingforest.moyeota.rating.dto;

import team.codingforest.moyeota.rating.entity.RatingLog;
import team.codingforest.moyeota.rating.entity.RatingType;

import java.time.LocalDateTime;

public record RatingResponse(
        Long matchId,
        Long raterId,
        Long rateeId,
        RatingType ratingType,
        byte rating,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RatingResponse from(RatingLog ratingLog) {
        return new RatingResponse(
                ratingLog.getMatchId(), ratingLog.getRaterId(), ratingLog.getRateeId(),
                ratingLog.getRatingType(), ratingLog.getRating(),
                ratingLog.getCreatedAt(), ratingLog.getUpdatedAt()
        );
    }
}
