package team.codingforest.moyeota.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.matching.api.MatchingStartedEvent;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 *  아웃박스(Event Publication Registry) 의미를 고정한다:
 *  발행은 발행자 트랜잭션과 함께 event_publication 에 기록되고, 리스너가 실패하면 "미완료"로 남아 재시도 대상이 된다.
 *  (리스너가 예외를 삼키면 완료로 기록돼 이 보장이 깨진다)
 */
@SpringBootTest
class EventPublicationRegistryTest {
    @Autowired ApplicationEventPublisher publisher;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 리스너가_실패한_이벤트는_미완료_발행_기록으로_남는다() {
        long ghostParty = 987_654L;   // 존재하지 않는 방 → DispatchListener 가 PARTY_NOT_FOUND 로 실패

        tx.executeWithoutResult(s -> publisher.publishEvent(new MatchingStartedEvent(ghostParty)));

        // 비동기 리스너가 돌고 실패로 끝날 때까지 잠깐 기다린다
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        Integer incomplete = 0;
        while(Instant.now().isBefore(deadline) && incomplete == 0) {
            incomplete = jdbc.queryForObject(
                    "select count(*) from event_publication where completion_date is null and serialized_event like ?",
                    Integer.class, "%" + ghostParty + "%");
            if(incomplete == 0) try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }

        assertThat(incomplete).as("실패한 리스너의 발행 기록은 재시도할 수 있게 남아야 한다").isGreaterThanOrEqualTo(1);
    }

    @Test
    void 발행_트랜잭션이_롤백되면_발행_기록도_남지_않는다() {
        long rolledBack = 987_655L;

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            publisher.publishEvent(new MatchingStartedEvent(rolledBack));
            throw new IllegalStateException("커밋 직전 실패");
        })).isInstanceOf(IllegalStateException.class);

        Integer rows = jdbc.queryForObject("select count(*) from event_publication where serialized_event like ?",
                Integer.class, "%" + rolledBack + "%");
        assertThat(rows).as("방이 없는데 배차만 시작되는 일이 없어야 한다 - outbox 의 존재 이유").isZero();
    }
}
