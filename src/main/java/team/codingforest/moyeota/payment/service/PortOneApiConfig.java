package team.codingforest.moyeota.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.payment.dto.PortOneBillingKeyResponse;

@Configuration
public class PortOneApiConfig {

    @Bean
    public RestClient portOneRestClient(@Value("${portone.api.secret}") String apiSecret) {
        return RestClient.builder()
                .baseUrl("https://api.portone.io")
                .defaultHeader("Authorization", "PortOne " + apiSecret)   // V2 인증 스킴
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}