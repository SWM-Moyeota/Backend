package team.codingforest.moyeota.dispatch.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.dispatch.application.DispatchService;
import team.codingforest.moyeota.dispatch.application.dto.CallStatusResponse;
import team.codingforest.moyeota.driver.api.CurrentDriver;
import team.codingforest.moyeota.matching.api.PartySummary;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "기사 콜", description = "기사 앱 - 콜 수락/거절/상태. 기사 신원은 토큰(@CurrentDriver)에서 추출")
@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
public class DispatchCallController {
    private final DispatchService dispatchService;

    @Operation(summary = "콜 수락", description = "선착순. 이미 마감됐거나 다른 기사가 먼저 수락했으면 409. 방 구성원인 기사의 셀프 배차는 409")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "수락 완료"), @ApiResponse(responseCode = "403", description = "DRIVER_CANNOT_RECEIVE - 검증 전/차량 없음/콜 꺼짐"), @ApiResponse(responseCode = "404", description = "DRIVER_NOT_REGISTERED"), @ApiResponse(responseCode = "409", description = "CALL_CLOSED / DRIVER_ALREADY_RIDING / SELF_DISPATCH_NOT_ALLOWED / DRIVER_ALREADY_ASSIGNED")})
    @PostMapping("/calls/{partyId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        dispatchService.acceptCall(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "콜 거절", description = "거절한 기사에게는 같은 방 콜을 다시 보내지 않는다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "거절 완료"), @ApiResponse(responseCode = "409", description = "CALL_CLOSED - 이미 마감된 콜")})
    @PostMapping("/calls/{partyId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        dispatchService.rejectCall(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "콜 열림 여부", description = "이 기사에게 아직 열려 있는 콜인지. 푸시를 놓쳤을 때 앱이 확인용으로 호출")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "open=true 면 수락 가능")})
    @GetMapping("/calls/{partyId}/status")
    public ResponseEntity<CallStatusResponse> status(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        return ResponseEntity.ok(CallStatusResponse.of(dispatchService.isCallOpen(partyId, driverId)));
    }

    @Operation(summary = "콜 상세", description = "출발지·목적지 좌표/이름, 인원, 예상 요금·시간. 열려 있는 콜만 조회 가능")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "방 요약"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "CALL_CLOSED")})
    @GetMapping("/calls/{partyId}")
    public ResponseEntity<PartySummary> getPartyDetail(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        return ResponseEntity.ok(dispatchService.getDetailRoom(driverId, partyId));
    }
}
