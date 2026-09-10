package team.codingforest.moyeota.driver.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.driver.api.CurrentDriver;
import team.codingforest.moyeota.driver.application.DriverApplicationService;
import team.codingforest.moyeota.driver.application.dto.DriverResult;
import team.codingforest.moyeota.driver.application.dto.RegisterDriverRequest;
import team.codingforest.moyeota.driver.application.dto.RegisterFcmTokenRequest;
import team.codingforest.moyeota.driver.application.dto.RegisterVehicleCommand;
import team.codingforest.moyeota.driver.application.dto.RegisterVehicleRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "기사", description = "기사 등록·검증·차량·콜 설정·FCM 토큰. 기사 = 유저의 확장(같은 계정·같은 토큰)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DriverController {
    private final DriverApplicationService service;

    // TODO 관리자 권한 도입 시 admin 전용으로 전환 - 현재는 관리자 화면이 없어 기사 본인의 셀프 승인을 허용한다(PoC)
    @Operation(summary = "자격 검증 (PoC 셀프 승인)", description = "PENDING → VERIFIED. 관리자 화면이 생기면 admin 전용으로 이동 예정")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "검증 완료"), @ApiResponse(responseCode = "404", description = "DRIVER_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "DRIVER_ALREADY_VERIFIED")})
    @PostMapping("/drivers/verify")
    public ResponseEntity<Void> verify(@CurrentDriver Long driverId) {
        service.verify(driverId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "차량 교체/등록", description = "등록 시 받은 차량을 바꿀 때. 좌석 2 이상")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "저장 완료"), @ApiResponse(responseCode = "400", description = "INVALID_VEHICLE_SEATS / INVALID_PLATE_NUMBER"), @ApiResponse(responseCode = "404", description = "DRIVER_NOT_FOUND")})
    @PostMapping("/drivers/vehicle")
    public ResponseEntity<Void> registerVehicle(@CurrentDriver Long driverId, @Valid @RequestBody RegisterVehicleRequest request) {
        service.registerVehicle(new RegisterVehicleCommand(driverId, request.seats(), request.plateNumber(), request.type()));

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "콜 수신 켜기")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "켜짐"), @ApiResponse(responseCode = "404", description = "DRIVER_NOT_FOUND")})
    @PostMapping("/drivers/call")
    public ResponseEntity<Void> enableCall(@CurrentDriver Long driverId) {
        service.enableCall(driverId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "콜 수신 끄기")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "꺼짐"), @ApiResponse(responseCode = "404", description = "DRIVER_NOT_FOUND")})
    @DeleteMapping("/drivers/call")
    public ResponseEntity<Void> disableCall(@CurrentDriver Long driverId) {
        service.disableCall(driverId);

        return ResponseEntity.noContent().build();
    }

    /** 기사 앱 진입 분기용 - 등록 전이면 404(DRIVER_NOT_REGISTERED), 등록됐으면 PENDING/VERIFIED 상태 반환 */
    @Operation(summary = "내 기사 정보", description = "기사 앱 진입 분기용. 미등록이면 404(DRIVER_NOT_REGISTERED) → 기사 등록 화면. name 은 유저 닉네임")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "기사 정보"), @ApiResponse(responseCode = "404", description = "DRIVER_NOT_REGISTERED")})
    @GetMapping("/drivers/me")
    public ResponseEntity<DriverResult> me(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.getByUserId(userId));
    }

    @Operation(summary = "기사 앱 FCM 토큰 등록/갱신", description = "로그인 직후와 onTokenRefresh 때 호출. 기기 1대 정책 - 새 토큰이 이전 토큰을 덮어씀")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "저장 완료"), @ApiResponse(responseCode = "400", description = "DRIVER_EMPTY_FCM_TOKEN"), @ApiResponse(responseCode = "404", description = "DRIVER_NOT_FOUND")})
    @PutMapping("/drivers/fcm-token")
    public ResponseEntity<Void> registerFcmToken(@CurrentDriver Long driverId, @Valid @RequestBody RegisterFcmTokenRequest request) {
        service.registerFcmToken(driverId, request.token());

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "기사 앱 FCM 토큰 삭제", description = "로그아웃 직전 호출. 안 지우면 다음 로그인 계정으로 이전 사용자 알림이 감")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료"), @ApiResponse(responseCode = "404", description = "DRIVER_NOT_FOUND")})
    @DeleteMapping("/drivers/fcm-token")
    public ResponseEntity<Void> removeFcmToken(@CurrentDriver Long driverId) {
        service.removeFcmToken(driverId);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "기사 등록", description = "phone-first 온보딩 마지막 단계. 회원(유저) 상태에서 자격번호·계좌·차량을 한 번에 등록 → PENDING")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "등록된 기사 정보"), @ApiResponse(responseCode = "400", description = "입력 누락 / INVALID_VEHICLE_SEATS"), @ApiResponse(responseCode = "409", description = "DRIVER_ALREADY_REGISTERED")})
    @PostMapping("/drivers")
    public ResponseEntity<DriverResult> register(@CurrentUser Long userId, @Valid @RequestBody RegisterDriverRequest request) {
        return ResponseEntity.ok(service.register(request.toCommand(userId)));
    }
}
