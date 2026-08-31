package team.codingforest.moyeota.report.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.report.application.ReportApplicationService;
import team.codingforest.moyeota.report.application.dto.CallResultRequest;
import team.codingforest.moyeota.report.application.dto.ReportRequest;
import team.codingforest.moyeota.report.application.dto.ReportResponse;


// TODO 인증 도입 후 reporterId는 토큰에서 추출
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportApplicationService service;

    @PostMapping
    public ResponseEntity<ReportResponse> report(@Valid @RequestBody ReportRequest request) {
        Long reportId = service.report(request.reporterId(), request.partyId(), request.latitude(), request.longitude());

        return ResponseEntity.ok(ReportResponse.of( reportId));
    }

    @PatchMapping("/{reportId}/call-result")
    public ResponseEntity<Void> confirmCall(@PathVariable Long reportId, @Valid @RequestBody CallResultRequest request) {
        service.confirmCall(reportId, request.called());

        return ResponseEntity.noContent().build();
    }
}