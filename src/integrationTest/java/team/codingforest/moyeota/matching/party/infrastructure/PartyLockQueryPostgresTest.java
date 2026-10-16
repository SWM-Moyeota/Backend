package team.codingforest.moyeota.matching.party.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartyVersion;
import team.codingforest.moyeota.matching.party.domain.Radius;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.profiles.active=dev")
class PartyLockQueryPostgresTest {
    @Autowired Parties parties;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void 잠금_조회가_멤버를_읽는다() {
        Party party = open(3);
        party.join(2L);
        Long id = parties.save(party).getId();

        Party locked = tx.execute(status -> parties.findByIdForUpdate(id).orElseThrow());

        assertThat(locked.getMembers()).extracting(PartyMember::getMemberId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void 멤버가_없는_방도_잠금_조회가_된다() {
        Long id = parties.save(open(3)).getId();
        tx.executeWithoutResult(status -> {
            Party p = parties.findByIdForUpdate(id).orElseThrow();
            p.leave(1L);
            parties.save(p);
        });

        Party locked = tx.execute(status -> parties.findByIdForUpdate(id).orElseThrow());

        assertThat(locked.getMembers()).isEmpty();
    }

    @Test
    void 잠금을_기다린_참여는_앞서_커밋된_참여를_보고_정원을_판정한다() throws Exception {
        Long id = parties.save(open(3)).getId();

        Party result = whileLocked(id, p -> p.join(2L), p -> p.join(3L));

        assertThat(result.getMembers()).extracting(PartyMember::getMemberId).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(result.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(result.getVersion()).as("커밋 하나에 1씩").isEqualTo(2);
    }

    @Test
    void 잠금을_기다린_나가기는_앞서_커밋된_나가기를_보고_방을_닫는다() throws Exception {
        Party party = open(3);
        party.join(2L);
        Long id = parties.save(party).getId();

        Party result = whileLocked(id, p -> p.leave(1L), p -> p.leave(2L));

        assertThat(result.getMembers()).isEmpty();
        assertThat(result.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(result.getVersion()).isEqualTo(3);
    }

    @Test
    void 방_번호들로_version_을_읽는다() {
        Party party = open(3);
        party.join(2L);
        Long id = parties.save(party).getId();

        assertThat(parties.findVersions(List.of(id, -1L)))
                .containsExactly(new PartyVersion(id, 1, PartyStatus.ACTIVE));
    }

    private Party whileLocked(Long id, Consumer<Party> first, Consumer<Party> second) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
            Party p = parties.findByIdForUpdate(id).orElseThrow();
            locked.countDown();
            awaitQuietly(release);
            first.accept(p);
            parties.save(p);
        }));
        locked.await();

        Future<Party> waiter = pool.submit(() -> tx.execute(status -> {
            Party p = parties.findByIdForUpdate(id).orElseThrow();
            second.accept(p);
            return parties.save(p);
        }));
        await().atMost(Duration.ofSeconds(5))
                .until(() -> jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock'", Integer.class) > 0);

        release.countDown();
        holder.get();
        return waiter.get();
    }

    private static Party open(int capacity) {
        return Party.open(1L, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                "강남역", "판교역", new Capacity(capacity), Instant.now(), new Radius(100), new Radius(100),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
