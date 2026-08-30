package team.codingforest.moyeota.dispatch.interfaces;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.dispatch.application.DispatchService;
import team.codingforest.moyeota.dispatch.application.dto.CallStatusResponse;
import team.codingforest.moyeota.matching.api.PartySummary;


@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
public class DispatchCallController {
    private final DispatchService dispatchService;

    @PostMapping("/calls/{partyId}/accept/{driverId}")
    public ResponseEntity<Void> accept(@PathVariable Long partyId, @PathVariable Long driverId) {
        dispatchService.acceptCall(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/calls/{partyId}/reject/{driverId}")
    public ResponseEntity<Void> reject(@PathVariable Long partyId, @PathVariable Long driverId) {
        dispatchService.rejectCall(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/calls/{partyId}/status")
    public ResponseEntity<CallStatusResponse> status(@PathVariable Long partyId, @RequestParam Long driverId) {
        return ResponseEntity.ok(CallStatusResponse.of(dispatchService.isCallOpen(partyId, driverId)));
    }

    @GetMapping("/calls/{partyId}/{driverId}")
    public ResponseEntity<PartySummary> getPartyDetail(@PathVariable Long driverId, @PathVariable Long partyId) {
        return ResponseEntity.ok(dispatchService.getDetailRoom(driverId, partyId));
    }
}