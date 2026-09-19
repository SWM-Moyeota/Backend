package team.codingforest.moyeota.rating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_rating")
@IdClass(UserRatingId.class)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRating {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "rating_type", nullable = false)
    private RatingType ratingType;

    @Column(name = "rating_sum", nullable = false)
    private int ratingSum;

    @Column(name = "total_ratings", nullable = false)
    private int totalRatings;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public UserRating(Long userId, RatingType ratingType) {
        this.userId = userId;
        this.ratingType = ratingType;
        this.ratingSum = 0;
        this.totalRatings = 0;
        this.avgRating = BigDecimal.ZERO;
    }

    public void add(byte rating) {
        ratingSum += rating;
        totalRatings++;
        calculateAverage();
    }

    public void replace(byte previousRating, byte newRating) {
        ratingSum += newRating - previousRating;
        calculateAverage();
    }

    public void remove(byte rating) {
        ratingSum -= rating;
        totalRatings--;
        calculateAverage();
    }

    public boolean isEmpty() {
        return totalRatings == 0;
    }

    private void calculateAverage() {
        if (totalRatings == 0) {
            avgRating = BigDecimal.ZERO;
            return;
        }

        avgRating = BigDecimal.valueOf(ratingSum)
                .divide(BigDecimal.valueOf(totalRatings), 2, RoundingMode.HALF_UP);
    }
}
