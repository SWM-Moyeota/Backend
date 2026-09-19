package team.codingforest.moyeota.searchhistory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 검색 기록 (search_history). 유저가 장소 검색에 입력한 검색어 한 건.
 * 유저별로 같은 검색어는 한 행만 둔다 - 다시 검색하면 기존 행을 지우고 새로 넣어 created_at 이 최신이 된다.
 */
@Entity
@Table(name = "search_history",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_search_history_user_query", columnNames = {"user_id", "search_query"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchHistory {
    public static final int MAX_QUERY_LENGTH = 100;

    /** 검색 아이디 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_history_id")
    private Long id;

    /** 유저 아이디 (users.id FK). 모듈 경계 때문에 연관관계 대신 id 만 든다 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 검색 내용 */
    @Column(name = "search_query", nullable = false, length = MAX_QUERY_LENGTH)
    private String searchQuery;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private SearchHistory(Long userId, String searchQuery, LocalDateTime createdAt) {
        this.userId = userId;
        this.searchQuery = searchQuery;
        this.createdAt = createdAt;
    }

    public static SearchHistory of(Long userId, String searchQuery, LocalDateTime createdAt) {
        return new SearchHistory(userId, searchQuery, createdAt);
    }
}
