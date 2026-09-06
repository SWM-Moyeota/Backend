package team.codingforest.moyeota.report.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.report.application.ReportApplicationService;
import team.codingforest.moyeota.report.application.dto.CallResultRequest;
import team.codingforest.moyeota.report.application.dto.ReportRequest;
import team.codingforest.moyeota.report.application.dto.ReportResponse;
import team.codingforest.moyeota.user.api.CurrentUser;


// TODO 인증 도입 후 reporterId는 토큰에서 추출
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportApplicationService service;

    @PostMapping
    public ResponseEntity<ReportResponse> report(@Valid @RequestBody ReportRequest request, @CurrentUser Long memberId) {
        Long reportId = service.report(memberId, request.partyId(), request.latitude(), request.longitude());

        return ResponseEntity.ok(ReportResponse.of( reportId));
    }

    @PatchMapping("/call-result")
    public ResponseEntity<Void> confirmCall(@CurrentUser Long memberId, @Valid @RequestBody CallResultRequest request) {
        service.confirmCall(memberId, request.called());

        return ResponseEntity.noContent().build();
    }
}