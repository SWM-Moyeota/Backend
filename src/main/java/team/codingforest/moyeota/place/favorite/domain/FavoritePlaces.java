package team.codingforest.moyeota.place.favorite.domain;

import java.util.List;

public interface FavoritePlaces {
    FavoritePlace save(FavoritePlace place);
    List<FavoritePlace> findByUserId(Long userId);
    int countByUserId(Long userId);
    boolean existsByUserIdAndPlace(Long userId, String placeName);
    /** @return 지운 건수. 없던 장소면 0 */
    int deleteByUserIdAndPlaceName(Long userId, String placeName);
    /** @return 지운 건수 */
    int deleteAllByUserId(Long userId);
}
