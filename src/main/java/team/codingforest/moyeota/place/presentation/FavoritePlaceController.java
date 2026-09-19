package team.codingforest.moyeota.place.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.place.application.FavoritePlaceApplicationService;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceListResponse;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceOrderRequest;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceRequest;
import team.codingforest.moyeota.place.application.dto.FavoritePlaceUpdateRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "즐겨찾기 장소", description = "집·회사 등 자주 쓰는 장소. 유저당 최대 10개, 이름은 유저 안에서 유일")
@RestController
@RequestMapping("/api/v1/users/me/favorite-places")
@RequiredArgsConstructor
public class FavoritePlaceController {
    private final FavoritePlaceApplicationService service;

    @Operation(summary = "즐겨찾기 등록", description = "순서는 맨 뒤로 붙는다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "등록 완료"),
            @ApiResponse(responseCode = "400", description = "INVALID_PLACE_NAME / INVALID_COORDINATES"),
            @ApiResponse(responseCode = "409", description = "FAVORITE_PLACE_DUPLICATED / FAVORITE_PLACE_LIMIT_EXCEEDED")})
    @PostMapping
    public ResponseEntity<Void> save(@RequestBody FavoritePlaceRequest request, @CurrentUser Long userId) {
        service.save(FavoritePlaceRequest.toCommand(request), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 즐겨찾기 목록", description = "placeSequence 오름차순")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "목록")})
    @GetMapping
    public ResponseEntity<FavoritePlaceListResponse> getFavoriteList(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.getList(userId));
    }

    @Operation(summary = "즐겨찾기 수정", description = "주소·좌표를 바꾼다. placeName 을 보내면 이름도 바꾼다(순서 유지)")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "수정 완료"),
            @ApiResponse(responseCode = "400", description = "INVALID_PLACE_NAME / INVALID_COORDINATES"),
            @ApiResponse(responseCode = "404", description = "FAVORITE_PLACE_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "FAVORITE_PLACE_DUPLICATED (바꾸려는 이름이 이미 있음)")})
    @PutMapping("/{placeName}")
    public ResponseEntity<Void> update(@PathVariable String placeName, @RequestBody FavoritePlaceUpdateRequest request, @CurrentUser Long userId) {
        service.update(FavoritePlaceUpdateRequest.toCommand(request), userId, placeName);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "즐겨찾기 삭제")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료"),
            @ApiResponse(responseCode = "404", description = "FAVORITE_PLACE_NOT_FOUND")})
    @DeleteMapping("/{placeName}")
    public ResponseEntity<Void> delete(@PathVariable String placeName, @CurrentUser Long userId) {
        service.delete(userId, placeName);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "즐겨찾기 순서 변경", description = "등록된 장소 이름 전체를 원하는 순서로 보낸다. 빠지거나 중복되면 400")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "변경 완료"),
            @ApiResponse(responseCode = "400", description = "INVALID_PLACE_ORDER")})
    @PatchMapping("/order")
    public ResponseEntity<Void> reorder(@RequestBody FavoritePlaceOrderRequest request, @CurrentUser Long userId) {
        service.reorder(request.placeNames(), userId);
        return ResponseEntity.noContent().build();
    }
}
