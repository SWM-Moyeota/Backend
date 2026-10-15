package team.codingforest.moyeota.chat.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompAuthInterceptor stompAuthInterceptor;

    @Value("${chat.websocket.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(allowedOrigins);
        registry.setPreserveReceiveOrder(true);   // 인바운드 스레드가 여러 개라 같은 세션의 연속 전송이 뒤바뀌어 저장될 수 있음
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/pub");
        registry.setPreservePublishOrder(true);   // 아웃바운드 스레드가 여러 개라 한 세션에 보내는 순서가 섞일 수 있음
        registry.enableSimpleBroker("/sub", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }

    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}
