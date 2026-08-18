package team.codingforest.moyeota.place.infrastructure;

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
        KakaoSearchResponse response = kakaoRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/keyword.json")
                        .queryParam("query", query)
                        .queryParam("size", 10)
                        .build())
                .retrieve()
                .body(KakaoSearchResponse.class);

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
