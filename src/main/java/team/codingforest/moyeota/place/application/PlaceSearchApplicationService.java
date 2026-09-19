package team.codingforest.moyeota.place.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.application.dto.PlaceSearchListResponse;
import team.codingforest.moyeota.place.application.dto.PlaceSearchResponse;
import team.codingforest.moyeota.place.domain.PlaceSearcher;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;
import team.codingforest.moyeota.searchhistory.service.SearchHistoryService;

import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class PlaceSearchApplicationService {
    /** 검색기록 컬럼 길이와 같다. 이보다 긴 검색어는 카카오에 보내기도 전에 거른다 */
    public static final int MAX_QUERY_LENGTH = 100;

    private final PlaceSearcher placeSearcher;
    private final SearchHistoryService searchHistoryService;

    /** 외부(카카오) 검색은 트랜잭션 밖에서 하고, 검색이 성공한 검색어만 기록한다 */
    public PlaceSearchListResponse search(String query, Long userId) {
        if(query == null || query.isBlank()) throw new BusinessException(PlaceErrorCode.SEARCH_QUERY_EMPTY);
        String normalized = query.strip();
        if(normalized.length() > MAX_QUERY_LENGTH) throw new BusinessException(PlaceErrorCode.SEARCH_QUERY_TOO_LONG);

        List<PlaceSearchResponse> list = placeSearcher.search(normalized)
                .stream().map(PlaceSearchResponse::toDto).toList();

        recordHistory(userId, normalized);
        return new PlaceSearchListResponse(list);
    }

    /** 검색기록 저장이 실패해도 검색 결과는 내려준다 - 기록은 부가 기능 */
    private void recordHistory(Long userId, String query) {
        try {
            searchHistoryService.record(userId, query);
        } catch (RuntimeException e) {
            log.warn("검색기록 저장 실패 userId={}, query={}", userId, query, e);
        }
    }
}
