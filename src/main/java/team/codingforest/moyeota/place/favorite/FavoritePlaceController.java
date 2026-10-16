package team.codingforest.moyeota.place.favorite;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceDeleteRequest;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "즐겨찾기 장소", description = "집·회사 등 자주 쓰는 장소")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
// TODO 추후 인증으로 유저 가져오기
public class FavoritePlaceController {
    private final FavoritePlaceService service;

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

    @Operation(summary = "즐겨찾기 삭제", description = "경로의 장소 ID로 내 즐겨찾기 한 건을 삭제한다.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료"), @ApiResponse(responseCode = "400", description = "INVALID_PLACE_ID - 장소 ID가 올바르지 않음"), @ApiResponse(responseCode = "404", description = "FAVORITE_PLACE_NOT_FOUND - 내 즐겨찾기에 없는 장소")})
    @DeleteMapping("/users/me/favorite-places/{placeId}")
    public ResponseEntity<Void> delete(@PathVariable("placeId") Long placeId, @CurrentUser Long userId) {
        service.delete(userId, placeId);
        return ResponseEntity.noContent().build();
    }


    @Operation(summary = "즐겨찾기 다건 삭제", description = "요청한 장소가 모두 내 즐겨찾기에 있어야 삭제한다.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료"), @ApiResponse(responseCode = "400", description = "INVALID_PLACE_ID / INVALID_PLACE_IDS"), @ApiResponse(responseCode = "404", description = "FAVORITE_PLACE_NOT_FOUND - 요청한 장소 중 내 즐겨찾기에 없는 항목이 있음")})
    @PostMapping("/users/me/favorite-places/batch-delete")
    public ResponseEntity<Void> deleteByPlaceIds(@RequestBody FavoritePlaceDeleteRequest request, @CurrentUser Long userId) {
        service.deleteByPlaceIds(userId, request.placeIds());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "즐겨찾기 전체 삭제", description = "내 즐겨찾기가 없으면 404를 반환한다.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료"), @ApiResponse(responseCode = "404", description = "FAVORITE_PLACE_NOT_FOUND - 내 즐겨찾기가 없음")})
    @DeleteMapping("/users/me/favorite-places/all")
    public ResponseEntity<Void> deleteAll(@CurrentUser Long userId) {
        service.deleteAll(userId);
        return ResponseEntity.noContent().build();
    }
}
