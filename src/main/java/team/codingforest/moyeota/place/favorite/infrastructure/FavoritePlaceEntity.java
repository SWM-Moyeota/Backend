package team.codingforest.moyeota.place.favorite.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlace;

import java.time.Instant;

@Entity
@EnableJpaAuditing
@Table(name = "favorite_place")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoritePlaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "user_id")
    private Long userId;

    private String roadName;

    private Double latitude;

    private Double longitude;

    private Integer placeSequence;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Column(name = "place_name")
    private String placeName;

    private FavoritePlaceEntity(Long userId, Long placeId, String roadName, Double latitude, Double longitude, Integer placeSequence, Instant createdAt, Instant updatedAt, String placeName) {
        this.userId = userId;
        this.placeId = placeId;
        this.roadName = roadName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeSequence = placeSequence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.placeName = placeName;
    }

    public static FavoritePlaceEntity from(FavoritePlace place) {
        return new FavoritePlaceEntity(place.getUserId(), place.getPlaceId(), place.getRoadName(), place.getLatitude(), place.getLongitude(), place.getPlaceSequence(), place.getCreatedAt(), place.getUpdatedAt(), place.getPlaceName());
    }

    public FavoritePlace toDomain() {
        return FavoritePlace.restore(userId, placeId, placeName, roadName, latitude, longitude, placeSequence, createdAt, updatedAt);
    }
}
