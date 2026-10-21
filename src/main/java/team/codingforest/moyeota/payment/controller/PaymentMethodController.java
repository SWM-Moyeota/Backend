package team.codingforest.moyeota.payment.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.payment.service.PaymentMethodService;
import team.codingforest.moyeota.payment.dto.PaymentMethodResponse;
import team.codingforest.moyeota.payment.dto.RegisterPaymentMethodRequest;
import team.codingforest.moyeota.user.api.CurrentUser;

@Tag(name = "결제수단", description = "포트원 빌링키 기반 자동결제 수단. 여러 카드를 보관하고 기본 카드 하나를 선택한다")
@RestController
@RequestMapping("/api/v1/payments/methods")
@RequiredArgsConstructor
public class PaymentMethodController {
    private final PaymentMethodService service;

    @Operation(summary = "카드 추가 등록", description = """
            앱이 포트원 SDK(requestIssueBillingKey)로 카드 빌링키를 발급받아 보낸다. 발급 시 customer.customerId 에 유저 publicId 를 넣어야 한다.
            서버는 포트원에서 빌링키를 다시 조회해 발급 상태·카드 여부·고객 식별자를 검증한 뒤 저장한다. 첫 카드는 기본 카드로 지정하고, 이후에는 기존 카드와 기본 카드 선택을 유지한다.
            같은 빌링키를 다시 보내면 등록된 수단을 그대로 돌려준다""")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "등록된 카드"),
            @ApiResponse(responseCode = "400", description = "INVALID_BILLING_KEY / UNSUPPORTED_PAYMENT_METHOD"),
            @ApiResponse(responseCode = "403", description = "BILLING_KEY_OWNER_MISMATCH"),
            @ApiResponse(responseCode = "404", description = "PAYMENT_USER_NOT_FOUND"),
            @ApiResponse(responseCode = "409", description = "PAYMENT_METHOD_CONFLICT"),
            @ApiResponse(responseCode = "502", description = "GATEWAY_ERROR")})
    @PostMapping
    public ResponseEntity<PaymentMethodResponse> register(@CurrentUser Long userId, @Valid @RequestBody RegisterPaymentMethodRequest request) {
        return ResponseEntity.ok(service.register(userId, request.billingKey()));
    }


    @Operation(summary = "등록 카드 목록 조회")
    @GetMapping
    public ResponseEntity<List<PaymentMethodResponse>> list(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.list(userId));
    }

    @Operation(summary = "기본 결제수단 선택", description = "본인의 사용 가능한 결제수단만 선택할 수 있다. 현재는 카드만 지원한다. 기존 결제수단은 삭제하지 않는다.")
    @PutMapping("/{methodId}/default")
    public ResponseEntity<PaymentMethodResponse> selectDefault(@CurrentUser Long userId,
            @PathVariable Long methodId) {
        return ResponseEntity.ok(service.selectDefault(userId, methodId));
    }


    @Operation(summary="카드 삭제", description="선택한 카드를 삭제한다.")
    @DeleteMapping("/card/{methodId}")
    public ResponseEntity<PaymentMethodResponse> deleteCard(@CurrentUser Long userId, @PathVariable Long methodId){
        return ResponseEntity.ok(service.deleteCard(userId, methodId));
    }



}
