package team.codingforest.moyeota.payment.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.payment.application.PaymentGroupService;
import team.codingforest.moyeota.payment.dto.PaymentGroupResponse;
import team.codingforest.moyeota.payment.dto.PaymentResponse;
import team.codingforest.moyeota.user.api.CurrentUser;

import java.util.List;

@Tag(name = "결제", description = """
        택시 동승 분할 결제. 운행 종료(기사 요금 입력) 시 서버가 방 구성원에게 요금을 나눠 자동 청구한다 - 앱이 결제를 시작하는 API 는 없다.
        실패한 분담금은 본인이 재시도하거나, 모든 청구가 끝난 뒤 멤버 한 명이 미수금을 대납할 수 있다""")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentGroupService service;

    @Operation(summary = "내 결제 내역", description = "분담금(SHARE)·대납(COVER) 전부, 최근 운행부터")
    @ApiResponse(responseCode = "200", description = "결제 목록")
    @GetMapping("/me")
    public ResponseEntity<List<PaymentResponse>> myPayments(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.findMyPayments(userId));
    }

    @Operation(summary = "결제 그룹 상세", description = "운행 1건의 분할 결제 현황 - 구성원별 상태·미수금. 방 구성원만")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "그룹 현황"), @ApiResponse(responseCode = "403", description = "NOT_GROUP_MEMBER"), @ApiResponse(responseCode = "404", description = "PAYMENT_GROUP_NOT_FOUND")})
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<PaymentGroupResponse> group(@PathVariable Long groupId, @CurrentUser Long userId) {
        return ResponseEntity.ok(service.findGroup(groupId, userId));
    }

    @Operation(summary = "실패한 분담금 재시도", description = "FAILED 인 본인 결제만. 결제수단을 등록/교체한 뒤 호출. 응답의 status 로 결과 확인(PAID / FAILED)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "재청구 결과"), @ApiResponse(responseCode = "403", description = "NOT_PAYMENT_OWNER"),
            @ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "PAYMENT_NOT_RETRYABLE / COVER_ALREADY_IN_PROGRESS"),
            @ApiResponse(responseCode = "502", description = "GATEWAY_ERROR - 결론 없음, 잠시 후 그룹 상세로 확인")})
    @PostMapping("/{paymentId}/retry")
    public ResponseEntity<PaymentResponse> retry(@PathVariable Long paymentId, @CurrentUser Long userId) {
        return ResponseEntity.ok(service.retry(paymentId, userId));
    }

    @Operation(summary = "미수금 대납", description = "그룹이 PARTIALLY_FAILED(모든 청구 완료, 실패분 존재)일 때 멤버 한 명이 남은 요금 전액을 결제. 성공하면 실패했던 분담금은 COVERED 로 종결")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "대납 결과"), @ApiResponse(responseCode = "403", description = "NOT_GROUP_MEMBER"),
            @ApiResponse(responseCode = "404", description = "PAYMENT_GROUP_NOT_FOUND"), @ApiResponse(responseCode = "409", description = "GROUP_NOT_COVERABLE / COVER_ALREADY_IN_PROGRESS"),
            @ApiResponse(responseCode = "502", description = "GATEWAY_ERROR")})
    @PostMapping("/groups/{groupId}/cover")
    public ResponseEntity<PaymentResponse> cover(@PathVariable Long groupId, @CurrentUser Long userId) {
        return ResponseEntity.ok(service.cover(groupId, userId));
    }
}
