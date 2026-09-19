package team.codingforest.moyeota.place.domain;

import lombok.Getter;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

import java.time.LocalDateTime;

@Getter
public class FavoritePlace {
    private final Long userId;
    private final String placeName;
    private String roadName;
    private Double latitude;
    private Double longitude;
    private Integer placeSequence;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private FavoritePlace(Long userId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence, LocalDateTime createdAt, LocalDateTime updatedAt) {
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
        validatePlaceName(placeName);
        validateCoordinates(latitude, longitude);

        LocalDateTime now = LocalDateTime.now();
        return new FavoritePlace(userId, placeName, roadName, latitude, longitude, placeSequence, now, now);
    }

    public static FavoritePlace restore(Long userId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new FavoritePlace(userId, placeName, roadName, latitude, longitude, placeSequence, createdAt, updatedAt);
    }

    /** 주소·좌표만 바꾼다. 이름(PK)은 {@link #rename(String)} 으로 새 객체를 만든다. */
    public void updateLocation(String roadName, Double latitude, Double longitude) {
        validateCoordinates(latitude, longitude);

        this.roadName = roadName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = LocalDateTime.now();
    }

    /** 이름은 PK 라 제자리 수정이 불가능하다. 순서·등록 시각을 유지한 새 객체를 돌려준다. */
    public FavoritePlace rename(String newPlaceName) {
        validatePlaceName(newPlaceName);

        return new FavoritePlace(userId, newPlaceName, roadName, latitude, longitude, placeSequence, createdAt, LocalDateTime.now());
    }

    public void updateSequence(Integer sequence) {
        this.placeSequence = sequence;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validatePlaceName(String placeName) {
        if(placeName == null || placeName.isBlank()) throw new BusinessException(PlaceErrorCode.INVALID_PLACE_NAME);
    }

    private static void validateCoordinates(Double latitude, Double longitude) {
        if(latitude == null || longitude == null
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new BusinessException(PlaceErrorCode.INVALID_COORDINATES);
        }
    }
}
