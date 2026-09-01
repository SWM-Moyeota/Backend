package team.codingforest.moyeota.place.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.application.dto.AddressResponse;
import team.codingforest.moyeota.place.domain.RegionSearcher;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

@Service
@RequiredArgsConstructor
public class ReverseGeocodingApplication {
    private final RegionSearcher regionSearcher;
    private static final double MIN_LAT = 33.0;
    private static final double MAX_LAT = 39.0;
    private static final double MIN_LNG = 124.0;
    private static final double MAX_LNG = 132.0;

    public AddressResponse findAddress(double latitude, double longitude) {
        if(latitude < MIN_LAT || latitude > MAX_LAT || longitude < MIN_LNG || longitude > MAX_LNG) throw new BusinessException(PlaceErrorCode.INVALID_COORDINATES);
        return regionSearcher.find(latitude, longitude)
                .map(AddressResponse::from)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.ADDRESS_NOT_FOUND));
    }
}
