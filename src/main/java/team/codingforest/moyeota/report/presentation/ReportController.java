package team.codingforest.moyeota.report.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.report.application.ReportApplicationService;
import team.codingforest.moyeota.report.application.dto.CallResultRequest;
import team.codingforest.moyeota.report.application.dto.ReportRequest;
import team.codingforest.moyeota.report.application.dto.ReportResponse;
import team.codingforest.moyeota.user.api.CurrentUser;


// TODO 인증 도입 후 reporterId는 토큰에서 추출
@Tag(name = "긴급 신고", description = "운행 중 승객의 112 신고 기록. 다이얼 전환 직전에 위치를 저장하고, 복귀 후 통화 여부를 확정")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportApplicationService service;

    @Operation(summary = "신고 사전 기록", description = "3초 홀드 완료 시 호출. 운행 중(IN_RIDE)인 방의 승객만. 저장 실패와 무관하게 앱은 다이얼을 연다")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "reportId"), @ApiResponse(responseCode = "403", description = "REPORT_NOT_ALLOWED - 운행 중인 방의 승객이 아님")})
    @PostMapping
    public ResponseEntity<ReportResponse> report(@Valid @RequestBody ReportRequest request, @CurrentUser Long memberId) {
        Long reportId = service.report(memberId, request.partyId(), request.latitude(), request.longitude());

        return ResponseEntity.ok(ReportResponse.of( reportId));
    }

    @Operation(summary = "통화 여부 확정", description = "앱 복귀 후 '112 와 통화했나요?' 응답. 본인의 최신 신고를 확정")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "확정 완료"), @ApiResponse(responseCode = "404", description = "REPORT_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "REPORT_ALREADY_CONFIRMED")})
    @PatchMapping("/call-result")
    public ResponseEntity<Void> confirmCall(@CurrentUser Long memberId, @Valid @RequestBody CallResultRequest request) {
        service.confirmCall(memberId, request.called());

        return ResponseEntity.noContent().build();
    }
}