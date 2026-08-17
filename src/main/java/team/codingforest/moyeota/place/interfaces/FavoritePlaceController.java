package team.codingforest.moyeota.place.interfaces;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.place.application.FavoritePlaceApplicationService;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceRequest;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
// TODO 추후 인증으로 유저 가져오기
public class FavoritePlaceController {
    private final FavoritePlaceApplicationService service;

    @PostMapping("/users/me/favorite-places")
    public ResponseEntity<Void> save(FavoritePlaceRequest request, Long userId) {
        service.save(FavoritePlaceRequest.toCommand(request), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/me/favorite-places")
    public ResponseEntity<FavoritePlaceListResponse> getFavoriteList(Long userId) {
        return ResponseEntity.ok(service.getList(userId));
    }
}
