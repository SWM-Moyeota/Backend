package team.codingforest.moyeota.place.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.place.domain.FavoritePlace;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [테이블] 자주가는 장소 (favorite_place)
 * <pre>
 * user_id        Long           복합 PK  유저 아이디
 * place_name     VarChar        복합 PK  사용자 지정 이름
 * road_name      VarChar                 지번
 * latitude       Decimal(11,8)           위도
 * longitude      Decimal(10,6)           경도
 * place_sequence Integer                 보여주는 순서
 * created_at     LocalDateTime           생성 시간
 * updated_at     LocalDateTime           변경 시간
 * </pre>
 */
@Entity
@IdClass(FavoritePlaceId.class)
@Table(name = "favorite_place")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoritePlaceEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "place_name", nullable = false, length = 255)
    private String placeName;

    @Column(name = "road_name", length = 255)
    private String roadName;

    @Column(name = "latitude", precision = 11, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(name = "place_sequence")
    private Integer placeSequence;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private FavoritePlaceEntity(Long userId, String placeName, String roadName, BigDecimal latitude, BigDecimal longitude, Integer placeSequence, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.userId = userId;
        this.placeName = placeName;
        this.roadName = roadName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeSequence = placeSequence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 시각은 도메인이 관리한다. 같은 PK 로 다시 저장(merge)해도 createdAt 이 덮이지 않는다. */
    public static FavoritePlaceEntity from(FavoritePlace place) {
        return new FavoritePlaceEntity(place.getUserId(), place.getPlaceName(), place.getRoadName(),
                toDecimal(place.getLatitude()), toDecimal(place.getLongitude()),
                place.getPlaceSequence(), place.getCreatedAt(), place.getUpdatedAt());
    }

    public FavoritePlace toDomain() {
        return FavoritePlace.restore(userId, placeName, roadName, toDouble(latitude), toDouble(longitude), placeSequence, createdAt, updatedAt);
    }

    private static BigDecimal toDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
