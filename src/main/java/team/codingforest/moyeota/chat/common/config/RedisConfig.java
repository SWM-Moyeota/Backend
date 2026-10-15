package team.codingforest.moyeota.chat.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import team.codingforest.moyeota.chat.common.redis.ChatRedisSubscriber;

@Slf4j
@Configuration
public class RedisConfig {
    public static final String CHAT_CHANNEL = "chat_channel";

    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic(CHAT_CHANNEL);
    }

    @Bean
    public ThreadPoolTaskExecutor chatSubscribeExecutor(MeterRegistry meterRegistry) {
        Counter dropped = meterRegistry.counter("chat.subscribe.dropped");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("chat-sub-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler((task, pool) -> {
            dropped.increment();
            log.warn("채팅 구독 큐가 가득 차 이벤트를 버림 queued={}", pool.getQueue().size());
        });
        return executor;
    }

    @Bean
    public RedisMessageListenerContainer chatRedisListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            ChatRedisSubscriber subscriber,
            ChannelTopic chatTopic,
            ThreadPoolTaskExecutor chatSubscribeExecutor
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.setTaskExecutor(chatSubscribeExecutor);
        container.addMessageListener(subscriber, chatTopic);
        return container;
    }
}
