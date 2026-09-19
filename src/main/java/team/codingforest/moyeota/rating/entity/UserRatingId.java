package team.codingforest.moyeota.rating.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserRatingId implements Serializable {

    private Long userId;
    private RatingType ratingType;

    protected UserRatingId() {
    }

    public UserRatingId(Long userId, RatingType ratingType) {
        this.userId = userId;
        this.ratingType = ratingType;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UserRatingId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && ratingType == that.ratingType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, ratingType);
    }
}
