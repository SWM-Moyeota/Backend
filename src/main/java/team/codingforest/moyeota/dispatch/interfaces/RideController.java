package team.codingforest.moyeota.dispatch.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.dispatch.application.RideService;
import team.codingforest.moyeota.dispatch.application.dto.CompleteRideRequest;

// TODO 인증 도입 후 driverId는 토큰에서 추출
@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
public class RideController {
    private final RideService rideService;

    @PostMapping("/rides/{partyId}/arrive/{driverId}")
    public ResponseEntity<Void> arrive(@PathVariable Long partyId, @PathVariable Long driverId) {
        rideService.arrive(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rides/{partyId}/board/{driverId}")
    public ResponseEntity<Void> board(@PathVariable Long partyId, @PathVariable Long driverId) {
        rideService.board(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rides/{partyId}/complete/{driverId}")
    public ResponseEntity<Void> complete(@PathVariable Long partyId, @PathVariable Long driverId,
                                         @Valid @RequestBody CompleteRideRequest request) {
        rideService.complete(partyId, driverId, request.fare());

        return ResponseEntity.noContent().build();
    }
}