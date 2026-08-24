package team.codingforest.moyeota.matching.interfaces;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.matching.application.*;
import team.codingforest.moyeota.matching.application.dto.OpenPartyRequest;
import team.codingforest.moyeota.matching.application.dto.OpenPartyResponse;
import team.codingforest.moyeota.matching.application.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.application.dto.PartyResult;
import team.codingforest.moyeota.matching.application.dto.*;
import team.codingforest.moyeota.matching.domain.RouteEstimate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PartyController {
    private final PartyApplicationService service;

    @PostMapping("/matching/rooms")
    public ResponseEntity<OpenPartyResponse> open(@RequestBody OpenPartyRequest request) {
        PartyResult party = service.open(request.toCommand());

        return ResponseEntity.ok(OpenPartyResponse.from(party));
    }

    @PostMapping("/matching/rooms/{partyId}/{memberId}/join")
    public ResponseEntity<PartyDetailResult> join(@PathVariable Long partyId, @PathVariable Long memberId) {
        return ResponseEntity.ok(service.join(partyId, memberId));
    }

    // TODO 추후 인증 관련 JWT 헤더에서 memberId 추출
    @DeleteMapping("/matching/leave/{partyId}/{memberId}")
    public ResponseEntity<Void> leave(@PathVariable Long partyId, @PathVariable Long memberId) {
        service.leave(partyId, memberId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/matching/ready/{partyId}/{memberId}")
    public ResponseEntity<Void> ready(@PathVariable Long partyId, @PathVariable Long memberId) {
        service.ready(partyId, memberId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/matching/ready/{partyId}/{memberId}")
    public ResponseEntity<Void> cancelReady(@PathVariable Long partyId, @PathVariable Long memberId) {
        service.cancelReady(partyId, memberId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/matching/start/{partyId}/{memberId}")
    public ResponseEntity<Void> startMatching(@PathVariable Long partyId, @PathVariable Long memberId) {
        service.startMatching(partyId, memberId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/matching/rooms/{partyId}")
    public ResponseEntity<PartyDetailResult> detail(@PathVariable Long partyId) {
        return ResponseEntity.ok(service.getPartyDetail(partyId));
    }

    @GetMapping("/matching/rooms")
    public ResponseEntity<PartyListResponse> list() {
        return ResponseEntity.ok(PartyListResponse.from(service.findActiveParties()));
    }

    @GetMapping("/matching/routes")
    public ResponseEntity<RouteEstimate> preView(@RequestBody RouteRequest req) {
        return ResponseEntity.ok(service.previewRoute(req.departureLat(), req.departureLng(), req.destinationLat(), req.destinationLng()));
    }
}
