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
import java.util.Set;
import java.util.stream.Collectors;

// TODO 순서 업데이트 로직 추가해야함
@Service
@RequiredArgsConstructor
@Slf4j
public class FavoritePlaceService {
    private final FavoritePlaces places;

    /**
     * 즐겨찾기 등록. placeId 는 DB 가 자동 채번한다
     * 같은 이름은 한 번만 등록할 수 있고, 최대 10개까지만 둔다
     */
    @Transactional
    public void save(FavoritePlaceCommand command, Long userId) {
        List<FavoritePlace> list = places.findByUserId(userId);

        if(list.stream().anyMatch(p -> p.getPlaceName().equals(command.placeName()))) {
            throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_DUPLICATED);
        }

        if(list.size() >= 10) throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_LIMIT_EXCEEDED);

        int nextSequence = list.stream()
                .mapToInt(FavoritePlace::getPlaceSequence)
                .max().orElse(0) + 1;

        FavoritePlace saved = places.save(command.toDomain(userId, nextSequence));
        log.info("자주가는 장소 등록 userId={}, placeId={}, placeName={}", userId, saved.getPlaceId(), saved.getPlaceName());
    }

    /**
     * 즐겨찾기 한 건 삭제
     * 삭제 전에 즐겨찾기의 존재 여부를 확인한다
     */
    @Transactional
    public void delete(Long userId, Long placeId) {
        if(placeId == null) throw new BusinessException(PlaceErrorCode.INVALID_PLACE_ID);
        //해당 즐겨찾기가 db에 존재하는지 확인한다.
        if(!places.existsByUserIdAndPlace(userId, placeId)) throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        places.deleteByUserIdAndPlaceName(userId, placeId);  // 즐겨찾기를 삭제한다.
        log.info("자주가는 장소 삭제 userId={}, placeId={}", userId, placeId);
    }


    /**
     * 요청한 장소가 모두 내 즐겨찾기에 있는지 확인한 뒤 한 번에 삭제한다.
     */
    @Transactional
    public void deleteByPlaceIds(Long userId, List<Long> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            throw new BusinessException(PlaceErrorCode.INVALID_PLACE_IDS);
        }

        if (placeIds.stream().anyMatch(id -> id == null)) {
            throw new BusinessException(PlaceErrorCode.INVALID_PLACE_ID);
        }

        List<Long> uniqueIds = placeIds.stream().distinct().toList();
        Set<Long> existingIds = places.findByUserId(userId).stream()
                .map(FavoritePlace::getPlaceId)
                .collect(Collectors.toSet());

        if (!existingIds.containsAll(uniqueIds)) {
            throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        }

        places.deleteByUserIdAndPlaceIdIn(userId, uniqueIds);
        log.info("자주가는 장소 다건 삭제 userId={}, requestedCount={}", userId, uniqueIds.size());
    }

    /**
     * 내 즐겨찾기가 있는지 확인한 뒤 전체 삭제한다.
     */
    @Transactional
    public void deleteAll(Long userId) {
        if (places.countByUserId(userId) == 0) {
            throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        }
        places.deleteAllByUserId(userId);
        log.info("자주가는 장소 전체 삭제 userId={}", userId);
    }

    @Transactional(readOnly = true)
    public FavoritePlaceListResponse getList(Long userId) {
        List<FavoritePlaceResponse> result = places.findByUserId(userId)
                .stream().map(FavoritePlaceResponse::toDto).toList();

        return new FavoritePlaceListResponse(result);
    }
}
