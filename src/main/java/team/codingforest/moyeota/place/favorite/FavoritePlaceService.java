package team.codingforest.moyeota.place.favorite;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceCommand;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceResponse;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlace;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlaces;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;

import java.util.List;

// TODO 순서 업데이트 로직 추가해야함
@Service
@RequiredArgsConstructor
@Slf4j
public class FavoritePlaceService {
    private final FavoritePlaces places;

    @Transactional
    public void save(FavoritePlaceCommand command, Long userId) {
        List<FavoritePlace> list = places.findByUserId(userId);

        if(places.existsByUserIdAndPlace(userId, command.placeName())) {
            throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_DUPLICATED);
        }

        if(list.size() >= 10) throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_LIMIT_EXCEEDED);

        int nextSequence = list.stream()
                .mapToInt(FavoritePlace::getPlaceSequence)
                .max().orElse(0) + 1;

        FavoritePlace place = command.toDomain(userId, nextSequence);
        place.updateSequence(nextSequence);

        log.info("자주가는 장소 등록 userId={}, placeName={}", place.getUserId(), place.getPlaceName());
        places.save(place);
    }

    /**
     * 즐겨찾기 한 건 삭제
     * 삭제 쿼리의 건수로 존재 여부를 판단해 exists → delete 사이 경쟁 구간을 없앤다
     */
    @Transactional
    public void delete(Long userId, String placeName) {
        if(placeName == null || placeName.isBlank()) throw new BusinessException(PlaceErrorCode.INVALID_PLACE_NAME);

        int deleted = places.deleteByUserIdAndPlaceName(userId, placeName);
        if(deleted == 0) throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);

        log.info("자주가는 장소 삭제 userId={}, placeName={}", userId, placeName);
    }

    /**
     * 내 즐겨찾기 전체 삭제. 지울 것이 없어도 성공으로 본다(멱등)
     */
    @Transactional
    public void deleteAll(Long userId) {
        int deleted = places.deleteAllByUserId(userId);
        log.info("자주가는 장소 전체 삭제 userId={}, count={}", userId, deleted);
    }

    @Transactional(readOnly = true)
    public FavoritePlaceListResponse getList(Long userId) {
        List<FavoritePlaceResponse> result = places.findByUserId(userId)
                .stream().map(FavoritePlaceResponse::toDto).toList();

        return new FavoritePlaceListResponse(result);
    }
}
