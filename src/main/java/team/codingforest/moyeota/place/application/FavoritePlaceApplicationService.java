package team.codingforest.moyeota.place.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceCommand;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceResponse;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceUpdateCommand;
import team.codingforest.moyeota.place.domain.FavoritePlace;
import team.codingforest.moyeota.place.domain.FavoritePlaces;
import team.codingforest.moyeota.place.domain.exception.PlaceErrorCode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FavoritePlaceApplicationService {
    static final int MAX_COUNT = 10;

    private final FavoritePlaces places;

    @Transactional
    public void save(FavoritePlaceCommand command, Long userId) {
        List<FavoritePlace> list = places.findByUserId(userId);

        if(places.existsByUserIdAndPlace(userId, command.placeName())) {
            throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_DUPLICATED);
        }

        if(list.size() >= MAX_COUNT) throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_LIMIT_EXCEEDED);

        // 중간이 삭제된 뒤에도 겹치지 않도록 size 가 아니라 max 기준
        int nextSequence = list.stream()
                .mapToInt(FavoritePlace::getPlaceSequence)
                .max().orElse(0) + 1;

        FavoritePlace place = command.toDomain(userId, nextSequence);

        log.info("자주가는 장소 등록 userId={}, placeName={}", place.getUserId(), place.getPlaceName());
        places.save(place);
    }

    @Transactional(readOnly = true)
    public FavoritePlaceListResponse getList(Long userId) {
        List<FavoritePlaceResponse> result = places.findByUserId(userId)
                .stream().map(FavoritePlaceResponse::toDto).toList();

        return new FavoritePlaceListResponse(result);
    }

    @Transactional
    public void update(FavoritePlaceUpdateCommand command, Long userId, String placeName) {
        FavoritePlace place = places.findByUserIdAndPlaceName(userId, placeName)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND));

        place.updateLocation(command.roadName(), command.latitude(), command.longitude());

        if(command.hasNewName() && !placeName.equals(command.placeName())) {
            if(places.existsByUserIdAndPlace(userId, command.placeName())) {
                throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_DUPLICATED);
            }

            // 이름이 PK 라 제자리 수정이 불가능 - 옛 행을 지우고 순서·등록 시각을 이어받은 새 행을 넣는다
            places.deleteByUserIdAndPlaceName(userId, placeName);
            places.save(place.rename(command.placeName()));

            log.info("자주가는 장소 이름 변경 userId={}, {} -> {}", userId, placeName, command.placeName());
            return;
        }

        places.save(place);
        log.info("자주가는 장소 수정 userId={}, placeName={}", userId, placeName);
    }

    @Transactional
    public void delete(Long userId, String placeName) {
        if(!places.existsByUserIdAndPlace(userId, placeName)) {
            throw new BusinessException(PlaceErrorCode.FAVORITE_PLACE_NOT_FOUND);
        }

        places.deleteByUserIdAndPlaceName(userId, placeName);
        log.info("자주가는 장소 삭제 userId={}, placeName={}", userId, placeName);
    }

    /** 등록된 장소 전체를 원하는 순서로 나열한 목록을 받아 1부터 다시 번호를 매긴다. */
    @Transactional
    public void reorder(List<String> orderedNames, Long userId) {
        List<FavoritePlace> list = places.findByUserId(userId);
        validateOrder(orderedNames, list);

        Map<String, FavoritePlace> byName = list.stream()
                .collect(Collectors.toMap(FavoritePlace::getPlaceName, Function.identity()));

        for(int i = 0; i < orderedNames.size(); i++) {
            byName.get(orderedNames.get(i)).updateSequence(i + 1);
        }

        places.saveAll(list);
        log.info("자주가는 장소 순서 변경 userId={}, order={}", userId, orderedNames);
    }

    private void validateOrder(List<String> orderedNames, List<FavoritePlace> current) {
        if(orderedNames == null) throw new BusinessException(PlaceErrorCode.INVALID_PLACE_ORDER);

        Set<String> currentNames = current.stream().map(FavoritePlace::getPlaceName).collect(Collectors.toSet());
        Set<String> requested = new HashSet<>(orderedNames);

        boolean hasDuplicate = requested.size() != orderedNames.size();
        boolean mismatch = !requested.equals(currentNames);

        if(hasDuplicate || mismatch) throw new BusinessException(PlaceErrorCode.INVALID_PLACE_ORDER);
    }
}
