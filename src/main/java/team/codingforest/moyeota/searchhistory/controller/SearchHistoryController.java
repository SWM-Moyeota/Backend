package team.codingforest.moyeota.searchhistory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.searchhistory.dto.SearchHistoryListResponse;
import team.codingforest.moyeota.searchhistory.service.SearchHistoryService;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "검색기록", description = "장소 검색에 입력한 최근 검색어. 장소 검색 API 를 호출하면 자동으로 쌓인다")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SearchHistoryController {
    private final SearchHistoryService service;

    @Operation(summary = "내 검색기록 목록", description = "최근 검색순. 최대 10개")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "목록")})
    @GetMapping("/users/me/search-histories")
    public ResponseEntity<SearchHistoryListResponse> getList(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.getList(userId));
    }

    @Operation(summary = "검색기록 하나 삭제")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료"), @ApiResponse(responseCode = "404", description = "SEARCH_HISTORY_NOT_FOUND - 없거나 내 기록이 아님")})
    @DeleteMapping("/users/me/search-histories/{historyId}")
    public ResponseEntity<Void> delete(@PathVariable Long historyId, @CurrentUser Long userId) {
        service.delete(userId, historyId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "검색기록 전체 삭제")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료")})
    @DeleteMapping("/users/me/search-histories")
    public ResponseEntity<Void> deleteAll(@CurrentUser Long userId) {
        service.deleteAll(userId);
        return ResponseEntity.noContent().build();
    }
}
