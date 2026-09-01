package team.codingforest.moyeota.matching.interfaces;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.driver.api.DriverSummary;
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

    @GetMapping("/matching/rooms/{partyId}")
    public ResponseEntity<PartyDetailResult> detail(@PathVariable Long partyId) {
        return ResponseEntity.ok(service.getPartyDetail(partyId));
    }

    @GetMapping("/matching/rooms")
    public ResponseEntity<PartyListResponse> list() {
        return ResponseEntity.ok(PartyListResponse.from(service.findActiveParties()));
    }

    @PostMapping("/matching/routes")
    public ResponseEntity<RouteEstimate> preView(@RequestBody RouteRequest req) {
        return ResponseEntity.ok(service.previewRoute(req.departureLat(), req.departureLng(), req.destinationLat(), req.destinationLng()));
    }

    @GetMapping("/matching/rooms/{partyId}/driver")
    public ResponseEntity<DriverSummary> findDriverSummary(@PathVariable Long partyId) {
        return ResponseEntity.ok(service.getAssignDriver(partyId));
    }

    // 같은 경로의 전체 목록 조회와 쿼리 파라미터 유무로 구분한다 (params 없이 두 개면 Ambiguous mapping으로 기동 실패)
    @GetMapping(value = "/matching/rooms", params = {"swLat", "swLng", "neLat", "neLng"})
    public ResponseEntity<PartyListResponse> listWithin(@RequestParam Double swLat,
                                                        @RequestParam Double swLng,
                                                        @RequestParam Double neLat,
                                                        @RequestParam Double neLng) {
        return ResponseEntity.ok(PartyListResponse.from(service.findActivePartiesWithin(swLat, swLng, neLat, neLng)));
    }
}
