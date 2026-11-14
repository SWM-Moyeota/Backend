package team.codingforest.moyeota.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 *  아웃박스(Event Publication Registry) 의미를 고정한다:
 *  발행은 발행자 트랜잭션과 함께 event_publication 에 기록되고, 리스너가 실패하면 "미완료"로 남아 재시도 대상이 된다.
 *  (리스너가 예외를 삼키면 완료로 기록돼 이 보장이 깨진다)
 *  운영 리스너는 실패를 삼키거나 모드에 따라 꺼질 수 있어, 이 테스트만 쓰는 이벤트와 늘 실패하는 리스너로 확인한다.
 */
@SpringBootTest
@Import(EventPublicationRegistryTest.FailingListenerConfig.class)
class EventPublicationRegistryTest {
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    /** 이 테스트만 발행하고 받는 이벤트 */
    public record OutboxProbeEvent(long probeId) {}

    /** 받는 즉시 실패하는 리스너 - 재시도 대상으로 남는지 보려고 쓴다 */
    public static class FailingListener {
        @ApplicationModuleListener
        public void on(OutboxProbeEvent event) {
            throw new IllegalStateException("아웃박스 확인용 리스너는 늘 실패한다 probeId=" + event.probeId());
        }
    }

    @TestConfiguration
    static class FailingListenerConfig {
        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }

    @AfterEach
    void tearDown() {
        // 남겨 두면 같은 DB 를 쓰는 다른 컨텍스트의 재발행 스케줄러가 주워 간다
        jdbc.update("delete from event_publication where event_type = ?", OutboxProbeEvent.class.getName());
    }

    @Test
    void 리스너가_실패한_이벤트는_미완료_발행_기록으로_남는다() {
        long probe = 987_654L;

        tx.executeWithoutResult(s -> publisher.publishEvent(new OutboxProbeEvent(probe)));

        // 비동기 리스너가 돌고 실패로 끝날 때까지 잠깐 기다린다
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        Integer failed = 0;
        while(Instant.now().isBefore(deadline) && failed == 0) {
            failed = jdbc.queryForObject(
                    "select count(*) from event_publication where completion_date is null and status = 'FAILED' and serialized_event like ?",
                    Integer.class, "%" + probe + "%");
            if(failed == 0) try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }

        assertThat(failed).as("실패한 리스너의 발행 기록은 재시도할 수 있게 남아야 한다").isEqualTo(1);
    }

    @Test
    void 발행_트랜잭션이_롤백되면_발행_기록도_남지_않는다() {
        long rolledBack = 987_655L;

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            publisher.publishEvent(new OutboxProbeEvent(rolledBack));
            throw new IllegalStateException("커밋 직전 실패");
        })).isInstanceOf(IllegalStateException.class);

        Integer rows = jdbc.queryForObject("select count(*) from event_publication where serialized_event like ?",
                Integer.class, "%" + rolledBack + "%");
        assertThat(rows).as("발행자 작업이 롤백됐는데 리스너만 도는 일이 없어야 한다 - outbox 의 존재 이유").isZero();
    }
}
