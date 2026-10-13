package team.codingforest.moyeota.place.favorite;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.place.favorite.domain.FavoritePlaces;
import team.codingforest.moyeota.user.api.UserDeletedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class FavoritePlaceUserDeletionListener {
    private final FavoritePlaces favoritePlaces;

    /**
     * 탈퇴한 사용자의 즐겨찾기를 지움
     * 자주 가는 장소는 위치 개인정보라 탈퇴하면 남기지 않음
     *
     * @param event 탈퇴한 사용자 정보
     */
    @ApplicationModuleListener
    public void on(UserDeletedEvent event) {
        favoritePlaces.deleteAllByUserId(event.userId());
        log.info("탈퇴한 사용자의 즐겨찾기 삭제 userId={}", event.userId());
    }
}
