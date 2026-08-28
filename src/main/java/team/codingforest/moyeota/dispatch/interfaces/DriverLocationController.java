package team.codingforest.moyeota.dispatch.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.dispatch.application.DriverLocationService;
import team.codingforest.moyeota.dispatch.application.dto.LocationReportRequest;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DriverLocationController {
    private final DriverLocationService service;

    // TODO 추후 토큰에서 유저 아이디 추출
    @PostMapping("/dispatch/online/{driverId}")
    public ResponseEntity<Void> goOnline(@PathVariable Long driverId, @Valid @RequestBody LocationReportRequest request) {
        service.goOnline(driverId, request.latitude(), request.longitude());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/dispatch/location/{driverId}")
    public ResponseEntity<Void> report(@PathVariable Long driverId, @Valid @RequestBody LocationReportRequest request) {
        service.report(driverId, request.latitude(), request.longitude());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/dispatch/online/{driverId}")
    public ResponseEntity<Void> goOffline(@PathVariable Long driverId) {
        service.goOffline(driverId);

        return ResponseEntity.noContent().build();
    }
}
