package team.codingforest.moyeota.chat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import team.codingforest.moyeota.chat.infra.ChatRedisSubscriber;

@Configuration
public class RedisConfig {
    public static final String CHAT_CHANNEL = "chat_channel";

    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic(CHAT_CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer chatRedisListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            ChatRedisSubscriber subscriber,
            ChannelTopic chatTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(subscriber, chatTopic);
        return container;
    }
}
