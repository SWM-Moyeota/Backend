package team.codingforest.moyeota.place.search.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class KakaoApiConfig {

    @Bean
    public RestClient kakaoRestClient(@Value("${kakao.api.key}") String apiKey, @Value("${api.time-out}") Integer timeOut) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(timeOut));
		factory.setReadTimeout(Duration.ofSeconds(timeOut));

        return RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + apiKey)
				.requestFactory(factory)
                .build();
    }
}
