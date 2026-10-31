package team.codingforest.moyeota.place.search.domain;

import lombok.Getter;

import java.time.Instant;

public class SearchHistory {
    private final Long id;
    private final Long userId;
    private final String searchQuery;
    private final Instant createdAt;

    private SearchHistory(Long id, Long userId, String searchQuery) {
        this.id = id;
        this.userId = userId;
        this.searchQuery = searchQuery;
        this.createdAt = Instant.now();
    }

    public static SearchHistory from(Long id, Long userId, String searchQuery) {
        return new SearchHistory(id, userId, searchQuery);
    }

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getSearchQuery() {
		return searchQuery;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
