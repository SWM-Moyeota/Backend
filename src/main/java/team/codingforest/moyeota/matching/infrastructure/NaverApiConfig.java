package team.codingforest.moyeota.matching.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class NaverApiConfig {

    @Bean
    public RestClient naverRestClient(@Value("${naver.api.key-id}") String keyId,
                                      @Value("${naver.api.key}") String key) {
        return RestClient.builder()
                .baseUrl("https://maps.apigw.ntruss.com")
                .defaultHeader("x-ncp-apigw-api-key-id", keyId)
                .defaultHeader("x-ncp-apigw-api-key", key)
                .build();
    }
}
