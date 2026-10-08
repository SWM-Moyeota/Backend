package team.codingforest.moyeota._config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 *  @Scheduled 작업의 분산 잠금. 서버가 여러 대일 때 @SchedulerLock 이 붙은 작업은 한 대만 돈다.
 *  **/
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")   // 어노테이션에 lockAtMostFor 를 안 적은 작업의 상한
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "moyeota");
    }
}
