package team.codingforest.moyeota.socket.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ChatApiConfig {

    @Bean
    public RestClient chatApiRestClient(RestClient.Builder builder,
                                        @Value("${chat.api.base-url}") String baseUrl,
                                        @Value("${chat.api.timeout}") Duration timeout,
                                        @Value("${chat.api.internal-token}") String internalToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Token", internalToken)   // API InternalTokenFilter 가 확인함
                .build();
    }
}
