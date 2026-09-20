package team.codingforest.moyeota.user.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class PortOneIdentityConfig {
    @Bean
    public Clock identityClock() { return Clock.systemUTC(); }

    @Bean
    public RestClient portOneIdentityRestClient() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl("https://api.portone.io").requestFactory(factory).build();
    }
}
