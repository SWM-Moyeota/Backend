package team.codingforest.moyeota.chat.infrastructure.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(ChatSearchProperties.class)
public class ChatSearchConfiguration {
    @Bean(defaultCandidate = false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "chat.search", name = "enabled", havingValue = "true")
    org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler chatSearchScheduler() {
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-search-");
        return scheduler;
    }

    @Bean
    ElasticsearchMessageIndex elasticsearchMessageIndex(ChatSearchProperties properties) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout()).build());
        factory.setReadTimeout(properties.readTimeout());
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.url().toString()).requestFactory(factory);
        if (!properties.apiKey().isBlank()) builder.defaultHeader("Authorization", "ApiKey " + properties.apiKey());
        return new ElasticsearchMessageIndex(builder.build(), properties);
    }
}
