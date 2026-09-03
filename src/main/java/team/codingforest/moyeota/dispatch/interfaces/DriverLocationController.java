package team.codingforest.moyeota.dispatch.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.dispatch.application.DriverLocationService;
import team.codingforest.moyeota.dispatch.application.dto.LocationReportRequest;
import team.codingforest.moyeota.driver.api.CurrentDriver;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DriverLocationController {
    private final DriverLocationService service;

    @PostMapping("/dispatch/online")
    public ResponseEntity<Void> goOnline(@CurrentDriver Long driverId, @Valid @RequestBody LocationReportRequest request) {
        service.goOnline(driverId, request.latitude(), request.longitude());

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/dispatch/location")
    public ResponseEntity<Void> report(@CurrentDriver Long driverId, @Valid @RequestBody LocationReportRequest request) {
        service.report(driverId, request.latitude(), request.longitude());

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/dispatch/online")
    public ResponseEntity<Void> goOffline(@CurrentDriver Long driverId) {
        service.goOffline(driverId);

        return ResponseEntity.noContent().build();
    }
}
