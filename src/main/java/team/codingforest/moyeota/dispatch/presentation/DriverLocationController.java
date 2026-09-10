package team.codingforest.moyeota.dispatch.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.dispatch.application.DriverLocationService;
import team.codingforest.moyeota.dispatch.application.dto.LocationReportRequest;
import team.codingforest.moyeota.driver.api.CurrentDriver;

@ConditionalOnProperty(prefix = "moyeota.taxi", name = "enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "기사 위치", description = "기사 앱 - 영업 시작/종료, 위치 보고 (Redis GEO)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DriverLocationController {
    private final DriverLocationService service;

    @Operation(summary = "영업 시작", description = "현재 위치를 등록하고 콜 수신 대상이 된다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "등록 완료"), @ApiResponse(responseCode = "400", description = "좌표 누락")})
    @PostMapping("/dispatch/online")
    public ResponseEntity<Void> goOnline(@CurrentDriver Long driverId, @Valid @RequestBody LocationReportRequest request) {
        service.goOnline(driverId, request.latitude(), request.longitude());

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "위치 보고", description = "영업 중 주기적으로 호출(권장 5~10초). 승객의 기사 위치 조회와 반경 검색에 쓰인다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "갱신 완료"), @ApiResponse(responseCode = "400", description = "좌표 누락")})
    @PostMapping("/dispatch/location")
    public ResponseEntity<Void> report(@CurrentDriver Long driverId, @Valid @RequestBody LocationReportRequest request) {
        service.report(driverId, request.latitude(), request.longitude());

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "영업 종료", description = "위치를 지우고 콜 수신 대상에서 빠진다. 멱등")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "종료 완료")})
    @DeleteMapping("/dispatch/online")
    public ResponseEntity<Void> goOffline(@CurrentDriver Long driverId) {
        service.goOffline(driverId);

        return ResponseEntity.noContent().build();
    }
}
