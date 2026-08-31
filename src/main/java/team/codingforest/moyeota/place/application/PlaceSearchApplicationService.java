package team.codingforest.moyeota.place.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.place.application.dto.PlaceSearchListResponse;
import team.codingforest.moyeota.place.application.dto.PlaceSearchResponse;
import team.codingforest.moyeota.place.domain.PlaceSearcher;

import java.util.List;

@RequiredArgsConstructor
@Service
public class PlaceSearchApplicationService {
    private final PlaceSearcher placeSearcher;

    public PlaceSearchListResponse search(String query) {
        if(query == null || query.isBlank()) throw new BusinessException(PlaceErrorCode.SEARCH_QUERY_EMPTY);

        List<PlaceSearchResponse> list = placeSearcher.search(query)
                .stream().map(PlaceSearchResponse::toDto).toList();

        return new PlaceSearchListResponse(list);
    }
}
