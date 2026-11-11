package team.codingforest.moyeota.socket.order;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableConfigurationProperties(ChatOrderProperties.class)
public class ChatOrderConfig {

    // 빈 순번 포기 타이머와 조용한 방 정리만 돎 - 일이 가벼워 스레드 1개
    @Bean
    public TaskScheduler chatOrderScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("chat-order-");
        scheduler.setRemoveOnCancelPolicy(true);   // 빈 순번이 채워져 취소한 타이머를 바로 치움
        scheduler.initialize();
        return scheduler;
    }
}
