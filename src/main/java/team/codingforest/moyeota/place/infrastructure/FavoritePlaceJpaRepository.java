package team.codingforest.moyeota.place.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoritePlaceJpaRepository extends JpaRepository<FavoritePlaceEntity, FavoritePlaceId> {

    List<FavoritePlaceEntity> findByUserId(Long userId);

    @Query("select count(fp) from FavoritePlaceEntity fp where fp.userId = :userId")
    int countByUserId(@Param("userId") Long userId);

    @Query("select count(fp) > 0 from FavoritePlaceEntity  fp where fp.userId = :userId and fp.placeName = :placeName")
    boolean existsByUserIdAndPlace(@Param("userId") Long userId, @Param("placeName") String placeName);

    @Modifying
    @Query("delete from FavoritePlaceEntity fp where fp.userId = :userId and fp.placeName = :placeName")
    void deleteByUserIdAndPlaceName(@Param("userId") Long userId, @Param("placeName") String placeName);
}
