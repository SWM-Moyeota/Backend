package team.codingforest.moyeota.place.application;

import team.codingforest.moyeota.place.domain.FavoritePlace;
import team.codingforest.moyeota.place.domain.FavoritePlaces;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FavoritePlaceJpaTest implements FavoritePlaces {
    private final Map<String, FavoritePlace> store = new HashMap<>();

    private String key(Long userId, String placeName) {
        return userId + ":" + placeName;
    }

    @Override
    public FavoritePlace save(FavoritePlace place) {
        store.put(key(place.getUserId(), place.getPlaceName()), place);
        return place;
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
    public boolean existsByUserIdAndPlace(Long userId, String placeName) {
        return store.containsKey(key(userId, placeName));
    }

    @Override
    public void deleteByUserIdAndPlaceName(Long userId, String placeName) {
        store.remove(key(userId, placeName));
    }
}
