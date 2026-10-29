package team.codingforest.moyeota.place.geocoding;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.geo.ServiceArea;
import team.codingforest.moyeota.place.geocoding.dto.AddressResponse;
import team.codingforest.moyeota.place.geocoding.domain.RegionSearcher;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;

@Service
@RequiredArgsConstructor
public class ReverseGeocodingService {
    private final RegionSearcher regionSearcher;

    public AddressResponse findAddress(double latitude, double longitude) {
		if(!ServiceArea.contains(latitude, longitude)) throw new BusinessException(PlaceErrorCode.INVALID_COORDINATES);
        return regionSearcher.find(latitude, longitude)
                .map(AddressResponse::from)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.ADDRESS_NOT_FOUND));
    }
}
