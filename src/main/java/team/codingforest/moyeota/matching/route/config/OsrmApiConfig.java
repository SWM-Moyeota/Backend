package team.codingforest.moyeota.matching.route.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class OsrmApiConfig {

    @Bean
    public RestClient osrmRestClient(@Value("${osrm.base-url}") String baseUrl, @Value("${api.time-out}") Integer timeOut) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeOut));
        factory.setReadTimeout(Duration.ofSeconds(timeOut));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
