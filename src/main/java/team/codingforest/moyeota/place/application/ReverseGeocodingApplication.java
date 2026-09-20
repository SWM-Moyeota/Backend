package team.codingforest.moyeota.place.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.application.dto.AddressResponse;
import team.codingforest.moyeota.place.domain.Address;
import team.codingforest.moyeota.place.domain.AddressCache;
import team.codingforest.moyeota.place.domain.RegionSearcher;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

@Service
@RequiredArgsConstructor
public class ReverseGeocodingApplication {
    private static final double MIN_LAT = 33.0;
    private static final double MAX_LAT = 39.0;
    private static final double MIN_LNG = 124.0;
    private static final double MAX_LNG = 132.0;

    /** 캐시 키 정밀도. 소수 4자리 ≈ 11m - 같은 건물 앞에서 찍힌 좌표들이 한 키로 모인다 */
    private static final double KEY_SCALE = 10_000.0;

    private final RegionSearcher regionSearcher;
    private final AddressCache cache;

    public AddressResponse findAddress(double latitude, double longitude) {
        if(latitude < MIN_LAT || latitude > MAX_LAT || longitude < MIN_LNG || longitude > MAX_LNG) throw new BusinessException(PlaceErrorCode.INVALID_COORDINATES);

        double keyLat = round(latitude);
        double keyLng = round(longitude);

        Address address = cache.find(keyLat, keyLng).orElseGet(() -> {
            Address fetched = regionSearcher.find(latitude, longitude)   // 외부 호출은 원래 좌표로
                    .orElseThrow(() -> new BusinessException(PlaceErrorCode.ADDRESS_NOT_FOUND));
            cache.save(keyLat, keyLng, fetched);
            return fetched;
        });

        return AddressResponse.from(address);
    }

    static double round(double coordinate) {
        return Math.round(coordinate * KEY_SCALE) / KEY_SCALE;
    }
}
