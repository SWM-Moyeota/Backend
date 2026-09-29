package team.codingforest.moyeota.place.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.place.search.dto.PlaceSearchListResponse;

@Tag(name = "장소", description = "장소 검색(카카오 로컬)과 좌표→주소 변환(네이버 역지오코딩)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PlaceSearchController {
    private final PlaceSearchService service;

    @Operation(summary = "장소 검색", description = "키워드로 카카오 로컬 검색. 빈 검색어는 400")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "검색 결과"), @ApiResponse(responseCode = "400", description = "EMPTY_QUERY"), @ApiResponse(responseCode = "502", description = "PLACE_SEARCH_FAILED - 카카오 API 장애")})
    @GetMapping("/places")
    public ResponseEntity<PlaceSearchListResponse> search(@RequestParam String query) {
        return ResponseEntity.ok(service.search(query));
    }

}
