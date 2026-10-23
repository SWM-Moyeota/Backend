package team.codingforest.moyeota.place.favorite.domain;

import lombok.Getter;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;

import java.time.Instant;

@Getter
public class FavoritePlace {
    /** 저장 전에는 null. DB 가 자동 채번한 뒤 restore 로 돌아온 객체에만 값이 있다 */
    private final Long placeId;
    private final Long userId;
    private final String placeName;
    private final String roadName;
    private final Double latitude;
    private final Double longitude;
    private Integer placeSequence;
    private final Instant createdAt;
    private Instant updatedAt;

    private FavoritePlace(Long userId, Long placeId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.placeId = placeId;
        this.placeName = placeName;
        this.roadName = roadName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.placeSequence = placeSequence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 새 즐겨찾기. placeId 는 저장 시 DB 가 부여한다 */
    public static FavoritePlace from(Long userId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence) {
        if(placeName == null || placeName.isBlank()) throw new BusinessException(PlaceErrorCode.INVALID_PLACE_NAME);

        return new FavoritePlace(userId, null, placeName, roadName, latitude, longitude, placeSequence, Instant.now(), Instant.now());
    }

    public static FavoritePlace restore(Long userId, Long placeId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence, Instant createdAt, Instant updatedAt) {
        return new FavoritePlace(userId, placeId, placeName, roadName, latitude, longitude, placeSequence, createdAt, updatedAt);
    }

    public void update() {
        this.updatedAt = Instant.now();
    }

    public void updateSequence(Integer count) {
        this.placeSequence = count;
    }
}
