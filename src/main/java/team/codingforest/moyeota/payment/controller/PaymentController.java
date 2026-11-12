package team.codingforest.moyeota.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.payment.dto.PaymentResultResponse;
import team.codingforest.moyeota.payment.dto.PaymentVerificationResponse;
import team.codingforest.moyeota.payment.service.PaymentService;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "결제", description = "사전결제 및 취소를 통한 결제 가능 여부 검증")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @Operation(summary = "결제 가능 여부 검증",
            description = "기본 결제수단으로 사전결제한 뒤 전액 취소합니다.")
    @PostMapping("/parties/{partyId}/verifications")
    public ResponseEntity<PaymentVerificationResponse> verifyPaymentCapability(
            @CurrentUser Long userId, @PathVariable Long partyId) {
        PaymentResultResponse payment = paymentService.prePay(userId, partyId);
        PaymentVerificationResponse verification = paymentService.cancelPrepayment(userId, payment.paymentId());
        return ResponseEntity.ok(verification);
    }
}
