package team.codingforest.moyeota._config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    public static final String REALTIME_EXECUTOR = "realtimeExecutor";

    @Bean
    @Primary
    public ThreadPoolTaskExecutor taskExecutor() {
        return executor("async-", 3, 1000);
    }

    @Bean
    public ThreadPoolTaskExecutor realtimeExecutor() {
        return executor("realtime-", 1, 10000);
    }

    private static ThreadPoolTaskExecutor executor(String prefix, int threads, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}