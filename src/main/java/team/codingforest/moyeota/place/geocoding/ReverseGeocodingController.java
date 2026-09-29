package team.codingforest.moyeota.place.geocoding;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.place.geocoding.dto.AddressResponse;

@Tag(name = "장소")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReverseGeocodingController {
    private final ReverseGeocodingService service;

    @Operation(summary = "좌표 → 주소", description = "지도 핀 위치의 도로명/지번 주소. 한국 범위 밖 좌표는 400")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "도로명·지번 주소와 표시용 문자열"), @ApiResponse(responseCode = "400", description = "OUT_OF_SERVICE_AREA"), @ApiResponse(responseCode = "404", description = "ADDRESS_NOT_FOUND"), @ApiResponse(responseCode = "502", description = "REVERSE_GEOCODING_FAILED")})
    @GetMapping("/places/reverse")
    public ResponseEntity<AddressResponse> reverse(@RequestParam double latitude, @RequestParam double longitude) {
        return ResponseEntity.ok(service.findAddress(latitude, longitude));
    }
}
