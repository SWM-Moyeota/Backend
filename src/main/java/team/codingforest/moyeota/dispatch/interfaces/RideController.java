package team.codingforest.moyeota.dispatch.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.dispatch.application.RideService;
import team.codingforest.moyeota.dispatch.application.dto.CompleteRideRequest;
import team.codingforest.moyeota.dispatch.application.dto.DriverLocationResponse;
import team.codingforest.moyeota.driver.api.CurrentDriver;
import team.codingforest.moyeota.user.api.CurrentUser;

@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
public class RideController {
    private final RideService rideService;

    @PostMapping("/rides/{partyId}/arrive")
    public ResponseEntity<Void> arrive(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        rideService.arrive(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rides/{partyId}/board")
    public ResponseEntity<Void> board(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        rideService.board(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rides/{partyId}/complete")
    public ResponseEntity<Void> complete(@PathVariable Long partyId, @CurrentDriver Long driverId,
                                         @Valid @RequestBody CompleteRideRequest request) {
        rideService.complete(partyId, driverId, request.fare());

        return ResponseEntity.noContent().build();
    }

    /** 승객이 배정 기사의 현재 위치를 폴링 조회 - 방 멤버만 */
    @GetMapping("/rides/{partyId}")
    public ResponseEntity<DriverLocationResponse> getLocation(@PathVariable Long partyId, @CurrentUser Long memberId) {
        return ResponseEntity.ok(rideService.driverLocation(partyId, memberId));
    }
}
