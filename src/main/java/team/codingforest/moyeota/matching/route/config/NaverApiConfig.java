package team.codingforest.moyeota.matching.route.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class NaverApiConfig {

    @Bean
    public RestClient naverRestClient(@Value("${naver.api.key-id}") String keyId,
                                      @Value("${naver.api.key}") String key,
									  @Value("${api.time-out}") Integer timeOut) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(timeOut));
		factory.setReadTimeout(Duration.ofSeconds(timeOut));

        return RestClient.builder()
                .baseUrl("https://maps.apigw.ntruss.com")
                .defaultHeader("x-ncp-apigw-api-key-id", keyId)
                .defaultHeader("x-ncp-apigw-api-key", key)
				.requestFactory(factory)
                .build();
    }
}
