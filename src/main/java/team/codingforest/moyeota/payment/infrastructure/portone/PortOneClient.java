package team.codingforest.moyeota.payment.infrastructure.portone;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.BillingKeyInfo;
import team.codingforest.moyeota.payment.domain.ChargeOrder;
import team.codingforest.moyeota.payment.domain.ChargeResult;
import team.codingforest.moyeota.payment.domain.GatewayPayment;
import team.codingforest.moyeota.payment.domain.PaymentGateway;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

/**
 *  포트원 V2 REST 어댑터.
 *  - 결제 자체의 거절(카드 한도·정지 등 4xx with type)은 ChargeResult.failed 로 돌려주고
 *  - 통신 장애·5xx·인증 설정 오류는 GATEWAY_ERROR 로 던진다 (호출자가 "결론 없음" 으로 다뤄야 하므로)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneClient implements PaymentGateway {
    private final RestClient portOneRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public BillingKeyInfo inspectBillingKey(String billingKey) {
        try {
            PortOneBillingKeyResponse response = portOneRestClient.get()
                    .uri("/billing-keys/{billingKey}", billingKey)
                    .retrieve()
                    .body(PortOneBillingKeyResponse.class);

            if(response == null) throw new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);

            return new BillingKeyInfo(response.isIssued(), response.customerId(), response.firstSupportedDetail());
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);
        } catch (RestClientException e) {
            throw gatewayError("빌링키 조회", e);
        }
    }

    @Override
    public ChargeResult charge(ChargeOrder order) {
        PortOneBillingKeyPaymentRequest request = new PortOneBillingKeyPaymentRequest(order.billingKey(), order.orderName(),
                new PortOneBillingKeyPaymentRequest.Amount(order.amount()), order.currency().name(),
                order.customerId() == null ? null : new PortOneBillingKeyPaymentRequest.Customer(order.customerId()));

        try {
            PortOneBillingKeyPaymentResponse response = portOneRestClient.post()
                    .uri("/payments/{paymentId}/billing-key", order.pgPaymentId())
                    .body(request)
                    .retrieve()
                    .body(PortOneBillingKeyPaymentResponse.class);

            Instant paidAt = response == null || response.payment() == null || response.payment().paidAt() == null ? Instant.now() : response.payment().paidAt();

            return ChargeResult.paid(paidAt);
        } catch (HttpStatusCodeException e) {
            if(isConfigOrServerProblem(e.getStatusCode())) throw gatewayError("빌링키 결제", e);

            PortOneErrorResponse error = parseError(e.getResponseBodyAsString());
            log.info("결제 거절 pgPaymentId={}, type={}, message={}", order.pgPaymentId(), error.type(), error.message());

            return ChargeResult.failed(error.failCode());
        } catch (RestClientException e) {
            throw gatewayError("빌링키 결제", e);
        }
    }

    @Override
    public Optional<GatewayPayment> findPayment(String pgPaymentId) {
        try {
            PortOnePaymentResponse response = portOneRestClient.get()
                    .uri("/payments/{paymentId}", pgPaymentId)
                    .retrieve()
                    .body(PortOnePaymentResponse.class);

            return Optional.ofNullable(response).map(PortOnePaymentResponse::toDomain);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw gatewayError("결제 조회", e);
        }
    }

    @Override
    public void cancel(String pgPaymentId, String reason) {
        try {
            portOneRestClient.post()
                    .uri("/payments/{paymentId}/cancel", pgPaymentId)
                    .body(new PortOneCancelRequest(reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw gatewayError("결제 취소", e);
        }
    }

    @Override
    public void deleteBillingKey(String billingKey) {
        try {
            portOneRestClient.delete()
                    .uri("/billing-keys/{billingKey}", billingKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            // 이미 지워진 키 - 목적은 달성
        } catch (RestClientException e) {
            throw gatewayError("빌링키 삭제", e);
        }
    }

    /** 401/403 은 우리 API 시크릿 문제, 5xx 는 결제사 장애 - 둘 다 사용자의 결제 실패가 아니다 */
    private static boolean isConfigOrServerProblem(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 401 || status.value() == 403;
    }

    private PortOneErrorResponse parseError(String body) {
        try {
            PortOneErrorResponse parsed = objectMapper.readValue(body, PortOneErrorResponse.class);
            return parsed != null ? parsed : new PortOneErrorResponse("UNKNOWN", body, null, null);
        } catch (RuntimeException e) {
            return new PortOneErrorResponse("UNKNOWN", body, null, null);
        }
    }

    private static BusinessException gatewayError(String action, Exception cause) {
        // 외부 장애가 정체불명의 500으로 새지 않게 규격 안(502)으로 변환
        log.warn("포트원 {} 실패", action, cause);
        return new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
    }
}
