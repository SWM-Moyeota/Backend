package team.codingforest.moyeota.matching.record;

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
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "탑승 기록", description = "내 탑승 기록. 택시 모드는 기사가 요금을 입력해 운행을 마친 방, 택시 기능이 꺼진 1차 모드는 방장이 합승 종료한 방이 남는다")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RideRecordController {
    private final RideRecordService rideRecordService;

    @Operation(summary = "내 탑승 기록", description = "하차까지 끝난 탑승을 하차 시각 최신순(같으면 partyId 큰 순)으로 준다. 택시 모드는 기사가 요금을 입력해 운행을 마친 방, 1차 모드는 방장이 합승 종료한 방이다. 1차 모드 기록은 기사가 없어 pickupTime·totalFare·farePerPerson 이 null 이다. 자동 만료된 방, 기사를 못 구해 해산된 방, 아직 운행 중인 방은 나오지 않는다. 첫 페이지는 cursor 없이, 다음 페이지는 응답의 nextCursor 를 그대로 보낸다. 마지막 페이지면 nextCursor null, hasNext false. members 는 나를 포함해 합류 순이고, 탈퇴한 동승자는 publicId 는 그대로·nickname 은 \"탈퇴한 사용자\"·imageUrl 은 기본 이미지 URL 로 나온다(사용자 정보 자체가 없으면 셋 다 null). 기사 식별자는 내려주지 않는다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "탑승 기록 한 페이지(없으면 빈 배열)"), @ApiResponse(responseCode = "400", description = "INVALID_PAGE_SIZE - size 가 1~50 밖 / INVALID_CURSOR - cursor 가 없는 기록이거나 아직 하차 전인 기록 / INVALID_REQUEST - size·cursor 가 정수가 아님")})
    @GetMapping("/users/me/parties")
    public ResponseEntity<RideRecordResponse> findMyRides(@CurrentUser Long memberId,
                                                           @Parameter(description = "이전 응답의 nextCursor(마지막 기록의 partyId). 생략하거나 빈 값이면 첫 페이지") @RequestParam(required = false) Long cursor,
                                                           @Parameter(description = "한 페이지 개수(1~50). 범위 밖이면 400") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(rideRecordService.findMyRides(memberId, cursor, size));
    }
}
