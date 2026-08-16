package team.codingforest.moyeota.driver.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.driver.application.DriverApplicationService;
import team.codingforest.moyeota.driver.application.dto.DriverResult;
import team.codingforest.moyeota.driver.application.dto.RegisterDriverRequest;
import team.codingforest.moyeota.driver.application.dto.RegisterVehicleRequest;

// TODO 추후 인증 구현되고 토큰을 통해 유저 아이디 가져오기
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DriverController {
    private final DriverApplicationService service;

    // TODO 추후 기사 등록시 검증해야 되는 정보들 처리해야함 ex(번호판, 기사 면허증등)
    @PostMapping("/drivers")
    public ResponseEntity<DriverResult> register(@Valid @RequestBody RegisterDriverRequest request) {
        return ResponseEntity.ok(service.register(request.toCommand()));
    }

    // TODO 추후 기사 등록시 검증해야 되는 정보들 처리해야함 ex(번호판, 기사 면허증등)
    @PostMapping("/drivers/{driverId}/verify")
    public ResponseEntity<Void> verify(@PathVariable Long driverId) {
        service.verify(driverId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drivers/{driverId}/vehicle")
    public ResponseEntity<Void> registerVehicle(@PathVariable Long driverId, @Valid @RequestBody RegisterVehicleRequest request) {
        service.registerVehicle(driverId, request.seats(), request.plateNumber());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/drivers/{driverId}/call")
    public ResponseEntity<Void> enableCall(@PathVariable Long driverId) {
        service.enableCall(driverId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/drivers/{driverId}/call")
    public ResponseEntity<Void> disableCall(@PathVariable Long driverId) {
        service.disableCall(driverId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/drivers/users/{userId}")
    public ResponseEntity<DriverResult> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getByUserId(userId));
    }
}
