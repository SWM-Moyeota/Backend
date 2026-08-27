package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.payment.dto.PortOneBillingKeyResponse;
import team.codingforest.moyeota.payment.dto.PortOneBillingPaymentRequest;
import team.codingforest.moyeota.payment.dto.PortOneBillingPaymentResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneBillingClient {
    private final RestClient portOneRestClient;

    public PortOneBillingKeyResponse getBillingKey(String billingKey) {
        return portOneRestClient.get()
                .uri("/billing-keys/{billingKey}", billingKey)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        (req, res) -> { throw new IllegalArgumentException("존재하지 않는 빌링키입니다."); })
                .body(PortOneBillingKeyResponse.class);
    }

    public PortOneBillingPaymentResponse payBillingKey(String paymentId, PortOneBillingPaymentRequest request) {
        return portOneRestClient.post()
                .uri("/payment/{paymentId}/billingKey", paymentId)
                .body(request)
                .retrieve()
                .onStatus(status -> status.value() == 409,
                (req, res) -> { throw new IllegalStateException("이미 결제된 paymentId입니다."); })
                .onStatus(HttpStatusCode::isError,
                        (req, res) -> { throw new IllegalStateException("빌링키 결제 실패했습니다."); })
                .body(PortOneBillingPaymentResponse.class);
    }
}
