package team.codingforest.moyeota.common.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 아웃박스(event_publication) 적체 게이지. 스크레이프 시점에만 count 쿼리가 실행된다.
 * 매칭 완료 → 배차 리스너가 밀리면 이 값이 쌓인다 - 부하테스트에서 비동기 처리 한계를 보는 지표.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublicationMetrics {
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry registry;

    @PostConstruct
    void register() {
        Gauge.builder("moyeota.event_publication.incomplete", this, EventPublicationMetrics::countIncomplete)
                .description("완료되지 않은 아웃박스 이벤트 수")
                .register(registry);
    }

    private double countIncomplete() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "select count(*) from event_publication where completion_date is null", Long.class);
            return count == null ? 0 : count;
        } catch (DataAccessException e) {
            log.debug("event_publication 조회 실패 - 게이지 생략", e);
            return Double.NaN;
        }
    }
}
