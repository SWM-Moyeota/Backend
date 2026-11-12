package team.codingforest.moyeota.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.dto.BillingKeyInfo;
import team.codingforest.moyeota.payment.dto.portone.PaymentAmount;
import team.codingforest.moyeota.payment.dto.portone.PortOnePaymentCancelReqeust;
import team.codingforest.moyeota.payment.dto.portone.PortOnePaymentRequest;
import team.codingforest.moyeota.payment.dto.portone.PortOnePaymentResponse;
import team.codingforest.moyeota.payment.entity.enums.PaymentCurrency;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

/**
 *  포트원 V2 REST 클라이언트.
 *  결제사에 없는 빌링키(404)는 INVALID_BILLING_KEY 로, 통신 장애·5xx·인증 설정 오류는 GATEWAY_ERROR 로 던진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneClient {
    private final RestClient portOneRestClient;

    /** 앱이 발급받아 보낸 빌링키를 결제사에서 조회. 결제사에 없는 키면 INVALID_BILLING_KEY, 통신 장애면 GATEWAY_ERROR */
    public BillingKeyInfo inspectBillingKey(String billingKey) {
        try {
            PortOneBillingKeyResponse response = portOneRestClient.get()
                    .uri("/billing-keys/{billingKey}", billingKey)
                    .retrieve()
                    .body(PortOneBillingKeyResponse.class);

            if(response == null) throw new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);

            return new BillingKeyInfo(response.isIssued(), response.customerId(), response.cardOrNull());
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);
        } catch (RestClientException e) {
            throw gatewayError("빌링키 조회", e);
        }
    }

    /** 결제사에 보관된 빌링키 삭제. 이미 없는 키는 성공으로 본다 */
    public void deleteBillingKey(String billingKey) {
        try {
            portOneRestClient.delete()
                    .uri("/billing-keys/{billingKey}", billingKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            // 이미 지워진 키 - 목적은 달성
        } catch (HttpClientErrorException.Conflict e) {
            try {
                PortOneError error = e.getResponseBodyAs(PortOneError.class);
                if(error != null && "BILLING_KEY_ALREADY_DELETED".equals(error.type())) return;
            } catch (RuntimeException ignored) {
                // 해석할 수 없는 응답은 삭제 성공으로 간주하지 않는다.
            }
            throw gatewayError("빌링키 삭제", e);
        } catch (RestClientException e) {
            throw gatewayError("빌링키 삭제", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PortOneError(String type) {}

    private static BusinessException gatewayError(String action, Exception cause) {
        // 외부 장애가 정체불명의 500으로 새지 않게 규격 안(502)으로 변환. 빌링키는 로그에 남기지 않는다
        // 예외 원문에는 요청 URL의 빌링키나 응답 개인정보가 포함될 수 있다.
        log.warn("포트원 {} 실패 exceptionType={}", action, cause.getClass().getSimpleName());
        return new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
    }

    /*결제 요청*/
    public PortOnePaymentResponse payWithBillingKey(String paymentId, String billingKey, Long estimatedFare){

        PortOnePaymentRequest paymentRequest = new PortOnePaymentRequest(
                billingKey, "모여타 사전 결제", new PaymentAmount(estimatedFare), PaymentCurrency.KRW);

        try{
            return portOneRestClient.post()
                    .uri("/payments/{paymentId}/billing-key",paymentId)
                    .body(paymentRequest)
                    .retrieve()
                    .body(PortOnePaymentResponse.class);

        }catch (RestClientException e) {
            throw gatewayError("빌링키 결제", e);
        }
    }


    /** 전액 취소를 요청하고 포트원의 JSON 응답을 그대로 반환한다. paymentId는 결제 요청 시 사용한 ID다. */
    public String cancelPayment(String paymentId, String reason) {
        if (paymentId == null || paymentId.isBlank() || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("paymentId와 reason은 필수입니다.");
        }

        try {
            return portOneRestClient.post()
                    .uri("/payments/{paymentId}/cancel", paymentId)
                    .body(new PortOnePaymentCancelReqeust(reason))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw gatewayError("결제 취소", e);
        }
    }

}
