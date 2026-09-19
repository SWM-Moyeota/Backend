package team.codingforest.moyeota.searchhistory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.searchhistory.entity.SearchHistory;

import java.util.List;
import java.util.Optional;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    /** 최근 검색순. 같은 시각이면 나중에 만들어진 것이 위 */
    List<SearchHistory> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<SearchHistory> findByIdAndUserId(Long id, Long userId);

    /** 같은 검색어를 다시 검색할 때 기존 행 제거. 즉시 실행되는 bulk 삭제라 뒤이은 insert 와 unique 충돌이 없다 */
    @Modifying
    @Query("delete from SearchHistory sh where sh.userId = :userId and sh.searchQuery = :searchQuery")
    void deleteByUserIdAndSearchQuery(@Param("userId") Long userId, @Param("searchQuery") String searchQuery);

    @Modifying
    @Query("delete from SearchHistory sh where sh.id in :ids")
    void deleteAllByIdIn(@Param("ids") List<Long> ids);

    @Modifying
    @Query("delete from SearchHistory sh where sh.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
