package team.codingforest.moyeota.matching.interfaces;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.matching.application.LocationShareService;
import team.codingforest.moyeota.matching.application.dto.SharingMembersResponse;

import java.util.List;

@RestController
@RequestMapping("/api/matching/rooms/{partyId}/share")
@RequiredArgsConstructor
public class LocationShareController {
    private final LocationShareService service;

    @PostMapping("/{memberId}")
    public ResponseEntity<Void> start(@PathVariable Long partyId, @PathVariable Long memberId) {
        service.start(partyId, memberId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> stop(@PathVariable Long partyId, @PathVariable Long memberId) {
        service.stop(partyId, memberId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<SharingMembersResponse> list(@PathVariable Long partyId) {
        return ResponseEntity.ok(new SharingMembersResponse(service.findSharingMemberIds(partyId)));
    }
}
