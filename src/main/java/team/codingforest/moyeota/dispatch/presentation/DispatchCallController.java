package team.codingforest.moyeota.dispatch.presentation;

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
@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
public class DispatchCallController {
    private final DispatchService dispatchService;

    @PostMapping("/calls/{partyId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        dispatchService.acceptCall(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/calls/{partyId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        dispatchService.rejectCall(partyId, driverId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/calls/{partyId}/status")
    public ResponseEntity<CallStatusResponse> status(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        return ResponseEntity.ok(CallStatusResponse.of(dispatchService.isCallOpen(partyId, driverId)));
    }

    @GetMapping("/calls/{partyId}")
    public ResponseEntity<PartySummary> getPartyDetail(@PathVariable Long partyId, @CurrentDriver Long driverId) {
        return ResponseEntity.ok(dispatchService.getDetailRoom(driverId, partyId));
    }
}
