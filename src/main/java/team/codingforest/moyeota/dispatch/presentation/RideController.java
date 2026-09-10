package team.codingforest.moyeota.dispatch.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.dispatch.application.RideService;
import team.codingforest.moyeota.dispatch.application.dto.CompleteRideRequest;
import team.codingforest.moyeota.dispatch.application.dto.DriverLocationResponse;
import team.codingforest.moyeota.driver.api.CurrentDriver;
import team.codingforest.moyeota.user.api.CurrentUser;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "운행", description = "기사 앱 - 도착/탑승/종료, 승객 앱 - 기사 위치 조회")
@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
public class RideController {
    private final RideService rideService;

    @Operation(summary = "출발지 도착 통보", description = "배정된 기사가 출발지에 도착. 방 구성원 전원에게 FCM(type=DRIVER_ARRIVED) 발송")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "통보 완료"), @ApiResponse(responseCode = "409", description = "NOT_AWAITING_PICKUP - 배정 기사가 아니거나 이미 탑승/종료")})
    @PostMapping("/rides/{partyId}/arrive")
    public ResponseEntity<Void> arrive(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        rideService.arrive(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "탑승 완료 (운행 시작)", description = "DRIVER_ASSIGNED → IN_RIDE")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "운행 시작"), @ApiResponse(responseCode = "403", description = "NOT_ASSIGNED_DRIVER"), @ApiResponse(responseCode = "409", description = "NOT_AWAITING_PICKUP")})
    @PostMapping("/rides/{partyId}/board")
    public ResponseEntity<Void> board(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        rideService.board(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "운행 종료", description = "IN_RIDE → FINISHED. 미터기 요금(fare, 원)을 입력")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "종료 완료"), @ApiResponse(responseCode = "400", description = "fare 누락/0 이하"), @ApiResponse(responseCode = "403", description = "NOT_ASSIGNED_DRIVER"), @ApiResponse(responseCode = "409", description = "NOT_RIDING")})
    @PostMapping("/rides/{partyId}/complete")
    public ResponseEntity<Void> complete(@PathVariable Long partyId, @CurrentDriver Long driverId,
                                         @Valid @RequestBody CompleteRideRequest request) {
        rideService.complete(partyId, driverId, request.fare());

        return ResponseEntity.noContent().build();
    }

    /** 승객이 배정 기사의 현재 위치를 폴링 조회 - 방 멤버만 */
    @Operation(summary = "배정 기사 현재 위치", description = "승객 앱 폴링용(권장 5초). 방 구성원만. 배정 전엔 409 이므로 파티 상세의 taxiDriverId 가 생긴 뒤에만 호출")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "경도/위도"), @ApiResponse(responseCode = "403", description = "NOT_PARTY_MEMBER"), @ApiResponse(responseCode = "404", description = "PARTY_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "DRIVER_NOT_ASSIGNED / DRIVER_LOCATION_UNAVAILABLE")})
    @GetMapping("/rides/{partyId}")
    public ResponseEntity<DriverLocationResponse> getLocation(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return ResponseEntity.ok(rideService.driverLocation(partyId, memberId));
    }
}
