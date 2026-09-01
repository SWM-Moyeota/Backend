package team.codingforest.moyeota.place.infrastructure;

import org.springframework.web.client.RestClientException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.place.domain.Place;
import team.codingforest.moyeota.place.domain.PlaceSearcher;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoPlaceClient implements PlaceSearcher {
    private final RestClient kakaoRestClient;

    @Override
    public List<Place> search(String query) {
        KakaoSearchResponse response;
        try {
            response = kakaoRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("size", 10)
                            .build())
                    .retrieve()
                    .body(KakaoSearchResponse.class);
        } catch (RestClientException e) {
            // 외부 장애가 정체불명의 500으로 새지 않게 규격 안(502)으로 변환
            log.warn("카카오 장소 검색 실패 query={}", query, e);
            throw new BusinessException(PlaceErrorCode.PLACE_SEARCH_FAILED);
        }

        if(response == null || response.documents() == null) return List.of();

        return response.documents().stream()
                .map(this::toPlaceOrNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private Place toPlaceOrNull(KakaoSearchResponse.KakaoPlaceDocument doc) {
        try {
            return new Place(doc.placeName(), doc.roadAddressName(),
                    Double.parseDouble(doc.y()),
                    Double.parseDouble(doc.x()));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("카카오 응답 항목 스킵: {}", doc);
            return null;
        }
    }
}
