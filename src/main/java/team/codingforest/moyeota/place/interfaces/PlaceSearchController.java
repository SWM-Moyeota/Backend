package team.codingforest.moyeota.place.interfaces;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.place.application.PlaceSearchApplicationService;
import team.codingforest.moyeota.place.application.dto.PlaceSearchListResponse;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PlaceSearchController {
    private final PlaceSearchApplicationService service;

    @GetMapping("/places")
    public ResponseEntity<PlaceSearchListResponse> search(@RequestParam String query) {
        return ResponseEntity.ok(service.search(query));
    }
}
