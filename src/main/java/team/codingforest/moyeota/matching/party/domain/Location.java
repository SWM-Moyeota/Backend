package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.geo.ServiceArea;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

public record Location(double latitude, double longitude) {
	// 지구 평균 반지름. 구로 보고 재므로 타원체(PostGIS geography) 거리와는 0.5% 안쪽으로 어긋난다 - 100m 판정에는 충분하다
	private static final double EARTH_RADIUS_METERS = 6_371_000;

	public Location {
		if(!ServiceArea.contains(latitude, longitude)) throw new BusinessException(MatchingErrorCode.OUT_OF_SERVICE_AREA);
	}

	/**
	 *  두 지점 사이의 대원 거리(m) - 하버사인 공식
	 */
	public double distanceMeters(Location other) {
		double fromLat = Math.toRadians(latitude);
		double toLat = Math.toRadians(other.latitude);
		double deltaLat = toLat - fromLat;
		double deltaLng = Math.toRadians(other.longitude - longitude);

		double a = Math.pow(Math.sin(deltaLat / 2), 2)
				+ Math.cos(fromLat) * Math.cos(toLat) * Math.pow(Math.sin(deltaLng / 2), 2);

		// 부동소수 오차로 a 가 1 을 살짝 넘으면 asin 이 NaN 이 된다
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
	}
}
