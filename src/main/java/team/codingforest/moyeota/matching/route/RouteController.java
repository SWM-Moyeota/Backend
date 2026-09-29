package team.codingforest.moyeota.matching.route;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;
import team.codingforest.moyeota.matching.route.dto.RouteRequest;

@Tag(name = "매칭방")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RouteController {
    private final RouteService routeService;

    @Operation(summary = "경로·요금 미리보기", description = "방 생성 전 예상 요금/시간/경로(polyline). 네이버 Directions 사용")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "예상 요금(원)·시간(분)·polyline"), @ApiResponse(responseCode = "400", description = "OUT_OF_SERVICE_AREA / INVALID_ROUTE_ESTIMATE"), @ApiResponse(responseCode = "404", description = "ROUTE_NOT_FOUND"), @ApiResponse(responseCode = "502", description = "ROUTE_SEARCH_FAILED")})
    @PostMapping("/matching/routes")
    public ResponseEntity<RouteEstimate> preView(@RequestBody RouteRequest req) {
        return ResponseEntity.ok(routeService.estimate(req.departureLat(), req.departureLng(), req.destinationLat(), req.destinationLng()));
    }
}
