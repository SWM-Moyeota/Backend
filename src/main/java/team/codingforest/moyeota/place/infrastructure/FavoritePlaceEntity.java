package team.codingforest.moyeota.place.infrastructure;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import team.codingforest.moyeota.place.domain.FavoritePlace;

import java.time.Instant;

@Entity
@IdClass(FavoritePlaceId.class)
@EnableJpaAuditing
@Table(name = "favorite_place")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FavoritePlaceEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "place_name")
    private String placeName;

    private String roadName;

    private Double latitude;

    private Double longitude;

    private Integer placeSequence;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private FavoritePlaceEntity(Long userId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.placeName = placeName;
        this.roadName = roadName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeSequence = placeSequence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FavoritePlaceEntity from(FavoritePlace place) {
        return new FavoritePlaceEntity(place.getUserId(), place.getPlaceName(), place.getRoadName(), place.getLatitude(), place.getLongitude(), place.getPlaceSequence(), Instant.now(), Instant.now());
    }

    public FavoritePlace toDomain() {
        return FavoritePlace.restore(userId, placeName, roadName, latitude, longitude, placeSequence, createdAt, updatedAt);
    }
}
