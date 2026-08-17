package team.codingforest.moyeota.place.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class FavoritePlace {
    private final Long userId;
    private final String placeName;
    private final String roadName;
    private final Double latitude;
    private final Double longitude;
    private Integer placeSequence;
    private final Instant createdAt;
    private Instant updatedAt;

    private FavoritePlace(Long userId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.placeName = placeName;
        this.roadName = roadName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeSequence = placeSequence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FavoritePlace from(Long userId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence) {
        return new FavoritePlace(userId, placeName, roadName, latitude, longitude, placeSequence, Instant.now(), Instant.now());
    }

    public static FavoritePlace restore(Long userId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence, Instant createdAt, Instant updatedAt) {
        return new FavoritePlace(userId, placeName, roadName, latitude, longitude, placeSequence, createdAt, updatedAt);
    }

    public void update() {
        this.updatedAt = Instant.now();
    }

    public void updateSequence(Integer count) {
        this.placeSequence = count;
    }
}
