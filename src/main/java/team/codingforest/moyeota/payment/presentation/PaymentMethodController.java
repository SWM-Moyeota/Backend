package team.codingforest.moyeota.payment.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.payment.application.PaymentMethodService;
import team.codingforest.moyeota.payment.dto.PaymentMethodResponse;
import team.codingforest.moyeota.payment.dto.RegisterPaymentMethodRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "결제수단", description = "포트원 빌링키 기반 자동결제 수단. 유저당 활성 수단 1개 - 새로 등록하면 이전 수단은 교체된다")
@RestController
@RequestMapping("/api/v1/payments/methods")
@RequiredArgsConstructor
public class PaymentMethodController {
    private final PaymentMethodService service;

    @Operation(summary = "결제수단 등록(교체)", description = """
            앱이 포트원 SDK(requestIssueBillingKey)로 빌링키를 발급받아 보낸다. 발급 시 customer.id 에 유저 publicId 를 넣어야 본인 수단으로 인정된다.
            서버는 포트원에서 빌링키를 다시 조회해 발급 상태·명의를 검증한 뒤 저장한다. 카드·간편결제·휴대폰 지원""")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "등록된 수단"), @ApiResponse(responseCode = "400", description = "INVALID_BILLING_KEY"),
            @ApiResponse(responseCode = "403", description = "BILLING_KEY_OWNER_MISMATCH"), @ApiResponse(responseCode = "502", description = "GATEWAY_ERROR")})
    @PostMapping
    public ResponseEntity<PaymentMethodResponse> register(@CurrentUser Long userId, @Valid @RequestBody RegisterPaymentMethodRequest request) {
        return ResponseEntity.ok(service.register(userId, request.billingKey()));
    }

    @Operation(summary = "내 결제수단", description = "운행 종료 후 자동 청구에 쓰일 수단. 없으면 404 → 등록 화면으로")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "활성 수단"), @ApiResponse(responseCode = "404", description = "PAYMENT_METHOD_NOT_FOUND")})
    @GetMapping("/me")
    public ResponseEntity<PaymentMethodResponse> me(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.findMine(userId));
    }

    @Operation(summary = "결제수단 삭제", description = "포트원 빌링키도 함께 삭제. 삭제 후 운행이 끝나면 청구가 NO_PAYMENT_METHOD 로 실패하므로 다시 등록 후 재시도해야 한다")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "삭제 완료"), @ApiResponse(responseCode = "404", description = "PAYMENT_METHOD_NOT_FOUND")})
    @DeleteMapping("/me")
    public ResponseEntity<Void> remove(@CurrentUser Long userId) {
        service.remove(userId);

        return ResponseEntity.noContent().build();
    }
}
