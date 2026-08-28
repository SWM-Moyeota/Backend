package team.codingforest.moyeota.place.domain;

import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoritePlaces {
    FavoritePlace save(FavoritePlace place);
    List<FavoritePlace> findByUserId(Long userId);
    int countByUserId(Long userId);
    boolean existsByUserIdAndPlace(Long userId, String placeName);
    void deleteByUserIdAndPlaceName(Long userId, String placeName);
}
