package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.payment.dto.PortOneBillingKeyResponse;

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
}
