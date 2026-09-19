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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "rating_log")
@IdClass(RatingLogId.class)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RatingLog {

    @Id
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Id
    @Column(name = "rater_id", nullable = false)
    private Long raterId;

    @Id
    @Column(name = "ratee_id", nullable = false)
    private Long rateeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating_type", nullable = false)
    private RatingType ratingType;

    @Column(nullable = false)
    private byte rating;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public RatingLog(Long matchId, Long raterId, Long rateeId, RatingType ratingType, byte rating) {
        this.matchId = matchId;
        this.raterId = raterId;
        this.rateeId = rateeId;
        this.ratingType = ratingType;
        this.rating = rating;
    }

    public void updateRating(byte rating) {
        this.rating = rating;
    }
}
