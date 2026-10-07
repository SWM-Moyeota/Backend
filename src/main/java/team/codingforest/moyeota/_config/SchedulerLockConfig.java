package team.codingforest.moyeota._config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 *  @Scheduled 작업의 분산 잠금. 서버가 여러 대일 때 @SchedulerLock 이 붙은 작업은 한 대만 돈다.
 *
 *  잠금은 Redis 에 "job-lock:moyeota:<작업 이름>" 키로 남는다(SET NX + 만료). 작업이 끝나면 풀리고,
 *  돌던 서버가 죽으면 lockAtMostFor 가 지난 뒤 다른 서버가 이어받는다.
 *
 *  잠그지 않는 것: PartySseRegistry.heartbeat - 각 서버가 자기 메모리의 SSE 연결에 ping 을 보내는 일이라
 *  서버마다 돌아야 한다. 잠그면 한 대의 연결만 살아남는다.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")   // 어노테이션에 lockAtMostFor 를 안 적은 작업의 상한
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "moyeota");
    }
}
