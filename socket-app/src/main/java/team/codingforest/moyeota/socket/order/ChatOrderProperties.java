package team.codingforest.moyeota.socket.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 순번 줄 세우기 설정 - application.yaml 의 chat.order
 *
 * @param maxWait      빈 순번을 기다리는 최대 시간
 * @param idleEviction 메시지가 이 시간 동안 없는 방은 줄을 지움
 */
@ConfigurationProperties("chat.order")
public record ChatOrderProperties(
        @DefaultValue("200ms") Duration maxWait,
        @DefaultValue("10m") Duration idleEviction
) {
}
