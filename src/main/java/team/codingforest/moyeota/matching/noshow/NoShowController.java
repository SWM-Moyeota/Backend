package team.codingforest.moyeota.matching.noshow;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.matching.noshow.dto.MyNoShowListResponse;
import team.codingforest.moyeota.matching.noshow.dto.NoShowObjectionRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "노쇼", description = "내 노쇼 기록과 이의 신청. 약속이 정해진 방에서 출발·자동 정리 때 도착하지 않았으면 노쇼다. 노쇼 1번마다 누적 횟수가 1 늘고, 그 방의 약속 시간부터 (누적 횟수 × 24시간) 동안 방 만들기·참여가 막힌다(403 NO_SHOW_RESTRICTED). 노쇼 없이 30일이 지날 때마다 누적 횟수가 1 준다. 지금 누적 횟수·제한 끝 시각은 내 프로필(GET /api/v1/local/users/info)의 noShowCount·restrictedUntil")
@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
public class NoShowController {
    private final NoShowObjectionService noShowObjectionService;

    @Operation(summary = "내 노쇼 기록", description = "노쇼로 기록된 방을 노쇼 시각 최신순으로 최대 20개. 운영자가 이의를 받아들여 무효로 돌린 노쇼도 voided=true 로 나온다. objectionStatus 는 그 방에 낸 이의 상태(내지 않았으면 null) - null 이고 voided=false 인 줄에 이의 신청 버튼을 보여 준다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "노쇼 기록(없으면 빈 배열)")})
    @GetMapping("/no-shows")
    public ResponseEntity<MyNoShowListResponse> findMyNoShows(@CurrentUser Long memberId) {
        return ResponseEntity.ok(MyNoShowListResponse.from(noShowObjectionService.findMyNoShows(memberId)));
    }

    @Operation(summary = "노쇼 이의 신청", description = "이 방에서 노쇼로 기록된(무효로 돌아가지 않은) 멤버가 방마다 한 번 낸다. 본문 reason 은 1~500자. 운영자가 받아들이면 그 노쇼가 무효가 되고(탑승 기록·탑승 횟수에 다시 잡힌다) 누적 횟수가 1 줄며 지금 걸린 이용 제한이 풀린다. 거절하면 그대로다. 처리 결과는 내 노쇼 기록의 objectionStatus 로 본다. 응답 본문 없음")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "신청됨(PENDING)"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - reason 누락·공백·500자 초과"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER - 이 방의 멤버가 아님"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "NOT_NO_SHOW - 이 방에서 노쇼가 아니거나 이미 무효로 돌아감 / ALREADY_OBJECTED - 이미 이의를 냄")})
    @PostMapping("/rooms/{partyId}/no-show-objection")
    public ResponseEntity<Void> object(@PathVariable Long partyId, @CurrentUser Long memberId, @Valid @RequestBody NoShowObjectionRequest request) {
        noShowObjectionService.object(partyId, memberId, request.reason());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
