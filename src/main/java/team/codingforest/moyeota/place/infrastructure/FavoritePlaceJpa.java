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
    public List<FavoritePlace> saveAll(List<FavoritePlace> places) {
        List<FavoritePlaceEntity> entities = places.stream().map(FavoritePlaceEntity::from).toList();
        return delegate.saveAll(entities).stream().map(FavoritePlaceEntity::toDomain).toList();
    }

    @Override
    public List<FavoritePlace> findByUserId(Long userId) {
        return delegate.findByUserIdOrderByPlaceSequenceAsc(userId)
                .stream().map(FavoritePlaceEntity::toDomain).toList();
    }

    @Override
    public Optional<FavoritePlace> findByUserIdAndPlaceName(Long userId, String placeName) {
        return delegate.findByUserIdAndPlaceName(userId, placeName).map(FavoritePlaceEntity::toDomain);
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
