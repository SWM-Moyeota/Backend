package team.codingforest.moyeota.driver.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.driver.api.CurrentDriver;
import team.codingforest.moyeota.driver.application.DriverApplicationService;
import team.codingforest.moyeota.driver.application.dto.*;
import team.codingforest.moyeota.user.api.CurrentUser;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DriverController {
    private final DriverApplicationService service;

    // TODO 관리자 권한 도입 시 admin 전용으로 전환 - 현재는 관리자 화면이 없어 기사 본인의 셀프 승인을 허용한다(PoC)
    @PostMapping("/drivers/verify")
    public ResponseEntity<Void> verify(@CurrentDriver Long driverId) {
        service.verify(driverId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drivers/vehicle")
    public ResponseEntity<Void> registerVehicle(@CurrentDriver Long driverId, @Valid @RequestBody RegisterVehicleRequest request) {
        service.registerVehicle(new RegisterVehicleCommand(driverId, request.seats(), request.plateNumber(), request.type()));

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drivers/call")
    public ResponseEntity<Void> enableCall(@CurrentDriver Long driverId) {
        service.enableCall(driverId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/drivers/call")
    public ResponseEntity<Void> disableCall(@CurrentDriver Long driverId) {
        service.disableCall(driverId);

        return ResponseEntity.noContent().build();
    }

    /** 기사 앱 진입 분기용 - 등록 전이면 404(DRIVER_NOT_REGISTERED), 등록됐으면 PENDING/VERIFIED 상태 반환 */
    @GetMapping("/drivers/me")
    public ResponseEntity<DriverResult> me(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.getByUserId(userId));
    }

    @PutMapping("/drivers/fcm-token")
    public ResponseEntity<Void> registerFcmToken(@CurrentDriver Long driverId, @Valid @RequestBody RegisterFcmTokenRequest request) {
        service.registerFcmToken(driverId, request.token());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/drivers/fcm-token")
    public ResponseEntity<Void> removeFcmToken(@CurrentDriver Long driverId) {
        service.removeFcmToken(driverId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drivers")
    public ResponseEntity<DriverResult> register(@CurrentUser Long userId, @Valid @RequestBody RegisterDriverRequest request) {
        return ResponseEntity.ok(service.register(request.toCommand(userId)));
    }
}
