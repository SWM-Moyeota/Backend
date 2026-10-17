package team.codingforest.moyeota.place.favorite;

import team.codingforest.moyeota.place.favorite.domain.FavoritePlace;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlaces;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** place_id 단일 PK 인 실제 테이블처럼 placeId 로만 행을 구분하고, 없으면 DB 처럼 자동 채번한다 */
class FakeFavoritePlaces implements FavoritePlaces {
    private final Map<Long, FavoritePlace> store = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public FavoritePlace save(FavoritePlace place) {
        Long id = place.getPlaceId() != null ? place.getPlaceId() : sequence.incrementAndGet();
        FavoritePlace saved = FavoritePlace.restore(place.getUserId(), id, place.getPlaceName(), place.getRoadName(),
                place.getLatitude(), place.getLongitude(), place.getPlaceSequence(), place.getCreatedAt(), place.getUpdatedAt());
        store.put(id, saved);
        return saved;
    }

    @Override
    public List<FavoritePlace> findByUserId(Long userId) {
        return store.values().stream()
                .filter(p -> p.getUserId().equals(userId))
                .sorted(Comparator.comparing(FavoritePlace::getPlaceSequence))
                .toList();
    }

    @Override
    public int countByUserId(Long userId) {
        return findByUserId(userId).size();
    }

    @Override
    public boolean existsByUserIdAndPlace(Long userId, Long placeId) {
        FavoritePlace place = store.get(placeId);
        return place != null && place.getUserId().equals(userId);
    }

    @Override
    public void deleteByUserIdAndPlaceName(Long userId, Long placeId) {
        if (existsByUserIdAndPlace(userId, placeId)) store.remove(placeId);
    }

    @Override
    public void deleteByUserIdAndPlaceIdIn(Long userId, List<Long> placeIds) {
        store.values().removeIf(p -> p.getUserId().equals(userId) && placeIds.contains(p.getPlaceId()));
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        store.values().removeIf(p -> p.getUserId().equals(userId));
    }
}
