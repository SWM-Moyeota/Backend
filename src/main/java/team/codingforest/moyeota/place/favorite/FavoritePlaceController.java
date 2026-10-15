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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.place.favorite.dto.FavoritePlaceListResponse;
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

    @Operation(summary = "즐겨찾기 삭제", description = "장소 이름으로 내 즐겨찾기 한 건을 지운다. 이름은 쿼리 파라미터로 받아 / 같은 특수문자가 있어도 된다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료"), @ApiResponse(responseCode = "400", description = "INVALID_PLACE_NAME - 이름이 비어 있음"), @ApiResponse(responseCode = "404", description = "FAVORITE_PLACE_NOT_FOUND - 내 즐겨찾기에 없는 장소")})
    @DeleteMapping("/users/me/favorite-places")
    public ResponseEntity<Void> delete(@RequestParam String placeName, @CurrentUser Long userId) {
        service.delete(userId, placeName);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "즐겨찾기 전체 삭제", description = "내 즐겨찾기를 모두 지운다. 지울 것이 없어도 204. 한 건 삭제에서 이름을 빠뜨렸을 때 전체가 지워지지 않도록 경로를 분리했다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료")})
    @DeleteMapping("/users/me/favorite-places/all")
    public ResponseEntity<Void> deleteAll(@CurrentUser Long userId) {
        service.deleteAll(userId);
        return ResponseEntity.noContent().build();
    }
}
