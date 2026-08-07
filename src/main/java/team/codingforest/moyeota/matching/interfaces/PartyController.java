package team.codingforest.moyeota.matching.interfaces;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.matching.application.*;
import team.codingforest.moyeota.matching.application.dto.OpenPartyRequest;
import team.codingforest.moyeota.matching.application.dto.OpenPartyResponse;
import team.codingforest.moyeota.matching.application.dto.PartyDetailResult;
import team.codingforest.moyeota.matching.application.dto.PartyResult;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PartyController {
    private final PartyApplicationService service;

    @PostMapping("/matching/rooms")
    public ResponseEntity<OpenPartyResponse> open(@RequestBody OpenPartyRequest request) {
        PartyResult party = service.open(request.toCommand());

        return ResponseEntity.ok(OpenPartyResponse.from(party));
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
}
