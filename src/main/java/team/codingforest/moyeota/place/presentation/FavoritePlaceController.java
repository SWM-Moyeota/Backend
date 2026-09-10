package team.codingforest.moyeota.place.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.place.application.FavoritePlaceApplicationService;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "즐겨찾기 장소", description = "집·회사 등 자주 쓰는 장소")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
// TODO 추후 인증으로 유저 가져오기
public class FavoritePlaceController {
    private final FavoritePlaceApplicationService service;

    @Operation(summary = "즐겨찾기 등록")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "등록 완료"), @ApiResponse(responseCode = "400", description = "INVALID_PLACE_NAME / 좌표 누락")})
    @PostMapping("/users/me/favorite-places")
    public ResponseEntity<Void> save(@RequestBody FavoritePlaceRequest request, @CurrentUser Long userId) {
        service.save(FavoritePlaceRequest.toCommand(request), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 즐겨찾기 목록")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "목록")})
    @GetMapping("/users/me/favorite-places")
    public ResponseEntity<FavoritePlaceListResponse> getFavoriteList(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.getList(userId));
    }
}
