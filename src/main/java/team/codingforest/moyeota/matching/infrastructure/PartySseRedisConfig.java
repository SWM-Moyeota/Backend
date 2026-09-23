package team.codingforest.moyeota.matching.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class PartySseRedisConfig {

    @Bean
    public RedisMessageListenerContainer partySseListenerContainer(RedisConnectionFactory factory, PartySseChannel channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(channel, new ChannelTopic(PartySseChannel.TOPIC));
        return container;
    }
}
