package team.codingforest.moyeota.place.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.application.dto.PlaceSearchListResponse;
import team.codingforest.moyeota.place.application.dto.PlaceSearchResponse;
import team.codingforest.moyeota.place.domain.Place;
import team.codingforest.moyeota.place.domain.PlaceSearchCache;
import team.codingforest.moyeota.place.domain.PlaceSearcher;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
@Service
public class PlaceSearchApplicationService {
    private final PlaceSearcher placeSearcher;
    private final PlaceSearchCache cache;

    public PlaceSearchListResponse search(String query) {
        if(query == null || query.isBlank()) throw new BusinessException(PlaceErrorCode.SEARCH_QUERY_EMPTY);

        String key = normalize(query);
        List<Place> places = cache.find(key).orElseGet(() -> {
            List<Place> fetched = placeSearcher.search(query);
            cache.save(key, fetched);   // 빈 결과도 저장 - 같은 오타 검색이 반복돼도 외부 호출을 막는다
            return fetched;
        });

        return new PlaceSearchListResponse(places.stream().map(PlaceSearchResponse::toDto).toList());
    }

    /** "강남역 " / "강남역" / "  강남역" 이 같은 캐시 키가 되도록: 앞뒤 공백 제거, 연속 공백 하나로, 소문자 */
    static String normalize(String query) {
        return query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
