package team.codingforest.moyeota.place.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.place.domain.FavoritePlace;
import team.codingforest.moyeota.place.domain.FavoritePlaces;

import java.util.List;
import java.util.Optional;

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
    public boolean existsByUserIdAndPlace(Long userId, String placeName) {
        return delegate.existsByUserIdAndPlace(userId, placeName);
    }

    @Override
    public void deleteByUserIdAndPlaceName(Long userId, String placeName) {
        delegate.deleteByUserIdAndPlaceName(userId, placeName);
    }
}
