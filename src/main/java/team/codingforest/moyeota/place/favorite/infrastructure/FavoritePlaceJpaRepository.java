package team.codingforest.moyeota.place.favorite.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoritePlaceJpaRepository extends JpaRepository<FavoritePlaceEntity, Long> {

    List<FavoritePlaceEntity> findByUserId(Long userId);

    @Query("select count(fp) from FavoritePlaceEntity fp where fp.userId = :userId")
    int countByUserId(@Param("userId") Long userId);

    @Query("select count(fp) > 0 from FavoritePlaceEntity  fp where fp.userId = :userId and fp.placeId = :placeId")
    boolean existsByUserIdAndPlace(@Param("userId") Long userId, @Param("placeId") Long placeId);

    @Modifying
    @Query("delete from FavoritePlaceEntity fp where fp.userId = :userId and fp.placeId = :placeId")
    void deleteByUserIdAndPlaceName(@Param("userId") Long userId, @Param("placeId") Long placeId);

    @Modifying
    @Query("delete from FavoritePlaceEntity fp where fp.userId = :userId and fp.placeId in :placeIds")
    void deleteByUserIdAndPlaceIdIn(@Param("userId") Long userId, @Param("placeIds") List<Long> placeIds);

    @Modifying
    @Query("delete from FavoritePlaceEntity fp where fp.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
