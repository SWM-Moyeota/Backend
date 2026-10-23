package team.codingforest.moyeota.matching.sse.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class PartySseSchedulerConfig {
    public static final String SCHEDULER = "partySseScheduler";

    @Bean(SCHEDULER)
    public ThreadPoolTaskScheduler partySseScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("party-sse-");
        return scheduler;
    }
}
