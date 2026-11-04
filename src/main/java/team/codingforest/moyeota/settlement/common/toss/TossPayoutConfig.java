package team.codingforest.moyeota.settlement.common.toss;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.settlement.account.domain.SellerGateway;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutGateway;
import tools.jackson.databind.ObjectMapper;

@ConditionalOnProperty(prefix = "moyeota.settlement.toss", name = "enabled", havingValue = "true")
@Configuration
public class TossPayoutConfig {

    @Bean
    public SellerGateway sellerGateway(TossPayoutClient tossPayoutClient) {
        return new TossSellerGateway(tossPayoutClient);
    }

    @Bean
    public PayoutGateway payoutGateway(TossPayoutClient tossPayoutClient) {
        return new TossPayoutGateway(tossPayoutClient);
    }

    @Bean
    public TossJweCipher tossJweCipher(TossPayoutProperties properties, ObjectMapper objectMapper) {
        return new TossJweCipher(properties.securityKey(), objectMapper);
    }

    @Bean
    public TossPayoutClient tossPayoutClient(TossPayoutProperties properties, TossJweCipher tossJweCipher, ObjectMapper objectMapper) {
        if(properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new IllegalStateException("토스 지급대행 시크릿 키가 없습니다.");
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = authorized(RestClient.builder().requestFactory(requestFactory), properties).build();
        return new TossPayoutClient(restClient, tossJweCipher, objectMapper);
    }

    static RestClient.Builder authorized(RestClient.Builder builder, TossPayoutProperties properties) {
        return builder.baseUrl(properties.baseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.secretKey(), ""));
    }
}
