package team.codingforest.moyeota.place.domain;

import java.util.List;
import java.util.Optional;

public interface FavoritePlaces {
    FavoritePlace save(FavoritePlace place);
    List<FavoritePlace> saveAll(List<FavoritePlace> places);
    /** placeSequence 오름차순 */
    List<FavoritePlace> findByUserId(Long userId);
    Optional<FavoritePlace> findByUserIdAndPlaceName(Long userId, String placeName);
    int countByUserId(Long userId);
    boolean existsByUserIdAndPlace(Long userId, String placeName);
    void deleteByUserIdAndPlaceName(Long userId, String placeName);
}
