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

@Tag(name = "탑승 기록", description = "내 탑승 기록. 출발을 누르거나 방장이 동승 종료한 방이 남는다. 노쇼로 기록된 사람에게는 남지 않는다")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RideRecordController {
    private final RideRecordService rideRecordService;

    @Operation(summary = "내 탑승 기록", description = "하차까지 끝난 탑승을 하차 시각 최신순(같으면 partyId 큰 순)으로 준다. 도착한 멤버가 출발을 누른 방(약속이 확정된 방), 출발을 누르지 않았지만 도착한 멤버가 있어 서버가 자동 정리한 방(실시간 방은 만든 지 1일, 예약 방은 출발 시각에서 1일 뒤)이나 방장이 동승 종료한 방(확정 전 방)이다. 출발·자동 정리 순간 도착하지 않아 노쇼로 기록된 사람에게는 그 방이 나오지 않고, 다른 사람 기록의 members·currentMembers 에서도 빠진다(운영자가 노쇼를 무효로 돌리면 다시 나온다). pickupTime 은 서버가 채우지 않아 null 이다(출시 앱 호환용으로 키만 남김). totalFare·farePerPerson 은 그 방에 택시비 정산이 있고 내가 받는 사람이거나 보내는 사람이면 정산 상태(진행 중·완료·만료)와 상관없이 채운다 - totalFare 는 정산의 택시비, farePerPerson 은 내 몫(보내는 사람은 1인당 금액, 받는 사람은 택시비에서 다른 사람들이 보낼 금액을 뺀 금액). 정산이 없거나(취소 포함) 내가 정산에 없으면(정산 시작 뒤 노쇼를 무효로 돌린 멤버 등) 둘 다 null 이다. 도착한 멤버 없이 자동 정리된 방(약속을 확정하지 않은 방 포함), 아직 끝나지 않은 방은 나오지 않는다. 첫 페이지는 cursor 없이, 다음 페이지는 응답의 nextCursor 를 그대로 보낸다. 마지막 페이지면 nextCursor null, hasNext false. members 는 나를 포함해 합류 순이고, 탈퇴한 동승자는 publicId 는 그대로·nickname 은 \"탈퇴한 사용자\"·imageUrl 은 기본 이미지 URL 로 나온다(사용자 정보 자체가 없으면 셋 다 null)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "탑승 기록 한 페이지(없으면 빈 배열)"), @ApiResponse(responseCode = "400", description = "INVALID_PAGE_SIZE - size 가 1~50 밖 / INVALID_CURSOR - cursor 가 없는 기록이거나 아직 하차 전인 기록 / INVALID_REQUEST - size·cursor 가 정수가 아님")})
    @GetMapping("/users/me/parties")
    public ResponseEntity<RideRecordResponse> findMyRides(@CurrentUser Long memberId,
                                                           @Parameter(description = "이전 응답의 nextCursor(마지막 기록의 partyId). 생략하거나 빈 값이면 첫 페이지") @RequestParam(required = false) Long cursor,
                                                           @Parameter(description = "한 페이지 개수(1~50). 범위 밖이면 400") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(rideRecordService.findMyRides(memberId, cursor, size));
    }
}
