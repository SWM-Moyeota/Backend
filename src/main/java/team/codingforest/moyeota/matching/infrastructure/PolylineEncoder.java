package team.codingforest.moyeota.matching.infrastructure;

import java.util.List;

/**
 *  Google Encoded Polyline Algorithm (정밀도 1e5).
 *  입력은 네이버 path 형식 [[lng, lat], ...] 이며, 표준대로 lat → lng 순서로 인코딩한다.
 */
public final class PolylineEncoder {
    private PolylineEncoder() {}

    public static String encode(List<List<Double>> path) {
        StringBuilder sb = new StringBuilder();
        long prevLat = 0, prevLng = 0;

        for(List<Double> point : path) {
            long lat = Math.round(point.get(1) * 1e5);   // [lng, lat] → lat 먼저
            long lng = Math.round(point.get(0) * 1e5);

            encodeValue(lat - prevLat, sb);
            encodeValue(lng - prevLng, sb);

            prevLat = lat;
            prevLng = lng;
        }

        return sb.toString();
    }

    private static void encodeValue(long v, StringBuilder sb) {
        v = v < 0 ? ~(v << 1) : (v << 1);
        while(v >= 0x20) {
            sb.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        sb.append((char) (v + 63));
    }
}