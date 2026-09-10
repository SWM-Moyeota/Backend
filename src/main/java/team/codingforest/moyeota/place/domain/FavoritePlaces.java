package team.codingforest.moyeota.place.domain;

import java.util.List;

public interface FavoritePlaces {
    FavoritePlace save(FavoritePlace place);
    List<FavoritePlace> findByUserId(Long userId);
    int countByUserId(Long userId);
    boolean existsByUserIdAndPlace(Long userId, String placeName);
    void deleteByUserIdAndPlaceName(Long userId, String placeName);
}
