package team.codingforest.moyeota.searchhistory.dto;

import team.codingforest.moyeota.searchhistory.entity.SearchHistory;

import java.time.LocalDateTime;

public record SearchHistoryResponse(Long id, String query, LocalDateTime createdAt) {

    public static SearchHistoryResponse from(SearchHistory history) {
        return new SearchHistoryResponse(history.getId(), history.getSearchQuery(), history.getCreatedAt());
    }
}
