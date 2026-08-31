package team.codingforest.moyeota.place.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceCommand;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceResponse;
import team.codingforest.moyeota.place.domain.FavoritePlace;
import team.codingforest.moyeota.place.domain.FavoritePlaces;

import java.util.List;

// TODO 순서 업데이트 로직 추가해야함
@Service
@RequiredArgsConstructor
@Slf4j
public class FavoritePlaceApplicationService {
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

    @Transactional(readOnly = true)
    public FavoritePlaceListResponse getList(Long userId) {
        List<FavoritePlaceResponse> result = places.findByUserId(userId)
                .stream().map(FavoritePlaceResponse::toDto).toList();

        return new FavoritePlaceListResponse(result);
    }
}
