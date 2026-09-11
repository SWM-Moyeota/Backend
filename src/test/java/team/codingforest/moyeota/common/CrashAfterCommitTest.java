package team.codingforest.moyeota.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import team.codingforest.moyeota.matching.application.PartyApplicationService;
import team.codingforest.moyeota.matching.domain.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 *  "커밋은 됐는데 리스너가 돌기 전에 프로세스가 죽은" 상황을 재현한다.
 *  비동기 리스너를 실행하는 TaskExecutor 를 '작업을 버리는' 것으로 바꾸면, 커밋 직후 스레드 풀과 함께 사라진 작업과 같다.
 *  이후 실행기를 살리고 재기동 시 재발행(republish-outstanding-events-on-restart)과 같은 API 를 부르면 리스너가 결국 실행돼야 한다.
 */
@SpringBootTest
@Import(CrashAfterCommitTest.CrashSimulation.class)
class CrashAfterCommitTest {

    @TestConfiguration
    static class CrashSimulation {
        static final AtomicBoolean crashed = new AtomicBoolean(false);

        @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
        @Primary
        TaskExecutor crashableExecutor() {
            return task -> {
                if(crashed.get()) return;                 // 프로세스 사망: 큐에 있던 작업이 그대로 증발
                Thread.ofVirtual().start(task);
            };
        }
    }

    @Autowired PartyApplicationService partyService;
    @Autowired Parties parties;
    @Autowired JdbcTemplate jdbc;
    @Autowired IncompleteEventPublications incompletePublications;

    @Test
    void 커밋_직후_죽어도_재기동_재발행으로_리스너가_결국_실행된다() {
        long base = 800_000L + System.currentTimeMillis() % 90_000L;
        Party party = parties.save(Party.open(base, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(2), Instant.now(), new Radius(100), new Radius(100), 12000, 25, "_p~iF~ps|U_ulLnnqC"));

        // 1. 서버가 "죽은" 상태에서 정원이 찬다 → 커밋은 되지만 리스너는 실행되지 않는다
        CrashSimulation.crashed.set(true);
        partyService.join(party.getId(), base + 1);

        assertThat(partyService.getPartyDetail(party.getId()).status()).as("방 상태는 커밋됨").isEqualTo("MATCHING");
        assertThat(incompleteRows(party.getId())).as("리스너가 안 돌았으니 발행 기록은 미완료로 남아 있어야 한다").isGreaterThanOrEqualTo(1);

        // 2. 서버 "재기동": 실행기 복구 + 미완료 발행 재제출 (republish-outstanding-events-on-restart 가 기동 시 하는 일)
        CrashSimulation.crashed.set(false);
        incompletePublications.resubmitIncompletePublications(p -> true);

        // 3. 리스너가 이번엔 끝까지 돌아 기록이 완료(DELETE 모드라 삭제)된다
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while(Instant.now().isBefore(deadline) && incompleteRows(party.getId()) > 0) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        assertThat(incompleteRows(party.getId())).as("재발행 후엔 미완료 기록이 없어야 한다").isZero();
    }

    private int incompleteRows(Long partyId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from event_publication where completion_date is null and serialized_event like ?",
                Integer.class, "%\"partyId\":" + partyId + "%");
        return n == null ? 0 : n;
    }
}
