package team.codingforest.moyeota.place.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.search.dto.PlaceSearchListResponse;
import team.codingforest.moyeota.place.search.dto.PlaceSearchResponse;
import team.codingforest.moyeota.place.search.domain.PlaceSearcher;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;

import java.util.List;

@RequiredArgsConstructor
@Service
public class PlaceSearchService {
    private final PlaceSearcher placeSearcher;

    public PlaceSearchListResponse search(String query) {
        if(query == null || query.isBlank()) throw new BusinessException(PlaceErrorCode.SEARCH_QUERY_EMPTY);

        List<PlaceSearchResponse> list = placeSearcher.search(query)
                .stream().map(PlaceSearchResponse::toDto).toList();

        return new PlaceSearchListResponse(list);
    }
}
