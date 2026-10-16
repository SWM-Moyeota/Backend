package team.codingforest.moyeota.place.favorite.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlace;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlaces;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FavoritePlaceJpa implements FavoritePlaces {

    private final FavoritePlaceJpaRepository delegate;

    @Override
    public FavoritePlace save(FavoritePlace place) {
        FavoritePlaceEntity entity = delegate.save(FavoritePlaceEntity.from(place));
        return entity.toDomain();
    }

    @Override
    public List<FavoritePlace> findByUserId(Long userId) {
        return delegate.findByUserId(userId)
                .stream().map(m -> m.toDomain()).toList();
    }

    @Override
    public int countByUserId(Long userId) {
        return delegate.countByUserId(userId);
    }

    @Override
    public boolean existsByUserIdAndPlace(Long userId, Long placeId) {
        return delegate.existsByUserIdAndPlace(userId, placeId);
    }

    @Override
    public void deleteByUserIdAndPlaceName(Long userId, Long placeId) {
        delegate.deleteByUserIdAndPlaceName(userId, placeId);
    }

    @Override
    public void deleteByUserIdAndPlaceIdIn(Long userId, List<Long> placeIds) {
        delegate.deleteByUserIdAndPlaceIdIn(userId, placeIds);
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        delegate.deleteAllByUserId(userId);
    }
}
