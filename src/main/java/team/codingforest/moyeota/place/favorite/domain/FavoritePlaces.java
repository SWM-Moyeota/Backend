package team.codingforest.moyeota.place.favorite.domain;

import java.util.List;

public interface FavoritePlaces {
    FavoritePlace save(FavoritePlace place);
    List<FavoritePlace> findByUserId(Long userId);
    int countByUserId(Long userId);
    boolean existsByUserIdAndPlace(Long userId, Long placeId);
    /** 지정한 즐겨찾기를 삭제한다. */
    void deleteByUserIdAndPlaceName(Long userId, Long placeId);
    /** 지정한 내 즐겨찾기를 삭제한다. 없는 항목은 무시한다. */
    void deleteByUserIdAndPlaceIdIn(Long userId, List<Long> placeIds);
    /** 사용자의 즐겨찾기를 모두 삭제한다. */
    void deleteAllByUserId(Long userId);
}
