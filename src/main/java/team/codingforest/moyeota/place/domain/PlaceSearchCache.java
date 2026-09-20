package team.codingforest.moyeota.place.domain;

import java.util.List;
import java.util.Optional;

/** 검색어 → 카카오 장소 검색 결과 캐시. 같은 검색어의 반복 호출이 외부 API 로 나가지 않게 한다. */
public interface PlaceSearchCache {
    Optional<List<Place>> find(String normalizedQuery);
    void save(String normalizedQuery, List<Place> places);
}
