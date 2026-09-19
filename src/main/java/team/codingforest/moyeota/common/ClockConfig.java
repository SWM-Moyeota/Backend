package team.codingforest.moyeota.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 시간을 주입받아야 하는 서비스용. 테스트에서는 고정 Clock 으로 바꿔 끼운다 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
