package team.codingforest.moyeota.common.geo;

public final class ServiceArea {
	private static final double MIN_LAT = 33.0;
	private static final double MAX_LAT = 39.0;
	private static final double MIN_LNG = 124.0;
	private static final double MAX_LNG = 132.0;

	private ServiceArea() {}

	public static boolean contains(double latitude, double longitude) {
		return latitude >= MIN_LAT && latitude <= MAX_LAT
				&& longitude >= MIN_LNG && longitude <= MAX_LNG;
	}
}
