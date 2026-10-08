package team.codingforest.moyeota.payment.infrastructure.portone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** 포트원 V2 REST. 인증은 "Authorization: PortOne {API_SECRET}" */
@Configuration
public class PortOneApiConfig {

    @Bean
    public RestClient portOneRestClient(@Value("${portone.api.secret}") String apiSecret,
                                        @Value("${portone.api.base-url:https://api.portone.io}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "PortOne " + apiSecret)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
