package team.codingforest.moyeota.place.favorite.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.geo.ServiceArea;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;

import java.time.Instant;

public class FavoritePlace {
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

	/**
	 * 새 즐겨찾기. placeId 는 저장 시 DB 가 부여한다
	 */
	public static FavoritePlace from(Long userId, String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence) {
		if (placeName == null || placeName.isBlank()) throw new BusinessException(PlaceErrorCode.INVALID_PLACE_NAME);
		if (latitude == null || longitude == null) throw new BusinessException(PlaceErrorCode.INVALID_COORDINATES);
		if (!ServiceArea.contains(latitude, longitude)) throw new BusinessException(PlaceErrorCode.INVALID_COORDINATES);

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

	public Long getPlaceId() {
		return placeId;
	}

	public Long getUserId() {
		return userId;
	}

	public String getPlaceName() {
		return placeName;
	}

	public String getRoadName() {
		return roadName;
	}

	public Double getLatitude() {
		return latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public Integer getPlaceSequence() {
		return placeSequence;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
