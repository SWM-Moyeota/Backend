package team.codingforest.moyeota._config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RedisLifeCycleConfig {

    public static final int REDIS_PHASE = -1000;

    @Bean
    static BeanPostProcessor redisConnectionPhase() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof LettuceConnectionFactory factory) {
                    factory.setPhase(REDIS_PHASE);
                }
                return bean;
            }
        };
    }
}
