package team.codingforest.moyeota.rating.cotroller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.rating.dto.RatingCreateRequest;
import team.codingforest.moyeota.rating.dto.RatingResponse;
import team.codingforest.moyeota.rating.dto.RatingUpdateRequest;
import team.codingforest.moyeota.rating.dto.UserRatingResponse;
import team.codingforest.moyeota.rating.entity.RatingType;
import team.codingforest.moyeota.rating.service.RatingService;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "평점", description = "매칭 참여자 평점 등록·조회·수정·삭제")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @Operation(summary = "평점 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 완료"),
            @ApiResponse(responseCode = "400", description = "평점 범위 오류 또는 자기 자신 평가"),
            @ApiResponse(responseCode = "409", description = "이미 등록한 평점")
    })
    @PostMapping("/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingResponse create(
            @CurrentUser Long raterId,
            @Valid @RequestBody RatingCreateRequest request
    ) {
        return ratingService.create(raterId, request);
    }

    @Operation(summary = "내가 등록한 평점 조회")
    @ApiResponse(responseCode = "404", description = "평점을 찾을 수 없음")
    @GetMapping("/ratings/matches/{matchId}/ratees/{rateeId}")
    public RatingResponse read(
            @CurrentUser Long raterId,
            @PathVariable Long matchId,
            @PathVariable Long rateeId
    ) {
        return ratingService.read(raterId, matchId, rateeId);
    }

    @Operation(summary = "사용자 누적 평점 조회")
    @GetMapping("/users/{userId}/ratings/{ratingType}")
    public UserRatingResponse readUserRating(
            @PathVariable Long userId,
            @PathVariable RatingType ratingType
    ) {
        return ratingService.readUserRating(userId, ratingType);
    }

    @Operation(summary = "평점 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 완료"),
            @ApiResponse(responseCode = "404", description = "평점을 찾을 수 없음")
    })
    @PatchMapping("/ratings/matches/{matchId}/ratees/{rateeId}")
    public RatingResponse update(
            @CurrentUser Long raterId,
            @PathVariable Long matchId,
            @PathVariable Long rateeId,
            @Valid @RequestBody RatingUpdateRequest request
    ) {
        return ratingService.update(raterId, matchId, rateeId, request.rating());
    }

    @Operation(summary = "평점 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 완료"),
            @ApiResponse(responseCode = "404", description = "평점을 찾을 수 없음")
    })
    @DeleteMapping("/ratings/matches/{matchId}/ratees/{rateeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @CurrentUser Long raterId,
            @PathVariable Long matchId,
            @PathVariable Long rateeId
    ) {
        ratingService.delete(raterId, matchId, rateeId);
    }
}
