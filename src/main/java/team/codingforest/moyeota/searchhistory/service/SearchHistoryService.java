package team.codingforest.moyeota.searchhistory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.searchhistory.dto.SearchHistoryListResponse;
import team.codingforest.moyeota.searchhistory.dto.SearchHistoryResponse;
import team.codingforest.moyeota.searchhistory.entity.SearchHistory;
import team.codingforest.moyeota.searchhistory.exception.SearchHistoryErrorCode;
import team.codingforest.moyeota.searchhistory.repository.SearchHistoryRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchHistoryService {
    /** 유저당 보관하는 최근 검색어 개수. 넘치면 오래된 것부터 지운다 */
    public static final int MAX_HISTORY_SIZE = 10;

    private final SearchHistoryRepository repository;
    private final Clock clock;

    /**
     * 검색어를 기록한다. 이미 있는 검색어면 기존 행을 지우고 새로 넣어 맨 위로 올리고, 개수가 넘치면 오래된 기록을 지운다.
     * 빈 검색어나 너무 긴 검색어는 검색 API 쪽에서 이미 걸러지므로 여기서는 조용히 무시한다.
     */
    @Transactional
    public void record(Long userId, String query) {
        if (query == null || query.isBlank()) return;
        String normalized = query.strip();
        if (normalized.length() > SearchHistory.MAX_QUERY_LENGTH) return;

        repository.deleteByUserIdAndSearchQuery(userId, normalized);
        repository.save(SearchHistory.of(userId, normalized, LocalDateTime.now(clock)));

        List<SearchHistory> all = repository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
        if (all.size() > MAX_HISTORY_SIZE) {
            List<Long> overflow = all.subList(MAX_HISTORY_SIZE, all.size())
                    .stream().map(SearchHistory::getId).toList();
            repository.deleteAllByIdIn(overflow);
        }
    }

    @Transactional(readOnly = true)
    public SearchHistoryListResponse getList(Long userId) {
        List<SearchHistoryResponse> result = repository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId)
                .stream().map(SearchHistoryResponse::from).toList();
        return new SearchHistoryListResponse(result);
    }

    /** 내 기록만 지울 수 있다. 남의 기록 id 는 없는 것과 똑같이 404 */
    @Transactional
    public void delete(Long userId, Long historyId) {
        SearchHistory history = repository.findByIdAndUserId(historyId, userId)
                .orElseThrow(() -> new BusinessException(SearchHistoryErrorCode.SEARCH_HISTORY_NOT_FOUND));
        repository.delete(history);
    }

    @Transactional
    public void deleteAll(Long userId) {
        repository.deleteAllByUserId(userId);
    }
}
