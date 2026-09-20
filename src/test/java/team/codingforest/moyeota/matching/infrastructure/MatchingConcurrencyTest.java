package team.codingforest.moyeota.matching.infrastructure;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.matching.application.*;
import team.codingforest.moyeota.matching.application.dto.*;
import team.codingforest.moyeota.matching.domain.*;
import team.codingforest.moyeota.user.api.UserAccess;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.enums.LoginType;
import team.codingforest.moyeota.user.infrastructure.*;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode.*;

/** 실제 PostgreSQL + 독립 커넥션. 기본 테스트 DB와 분리된 전용 스키마만 사용한다. */
@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=${CONCURRENCY_DB_URL:jdbc:postgresql://localhost:5432/moyeota}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=${CONCURRENCY_DB_USER:moyeota}",
        "spring.datasource.password=${CONCURRENCY_DB_PASSWORD:moyeota}",
        "spring.datasource.hikari.maximum-pool-size=16",
        "spring.datasource.hikari.connection-init-sql=SET search_path TO matching_concurrency_test",
        "spring.flyway.enabled=true", "spring.flyway.schemas=matching_concurrency_test",
        "spring.jpa.properties.hibernate.default_schema=matching_concurrency_test",
        "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PartyApplicationService.class, PartyJpa.class, ActiveMatchParticipationStore.class,
        MatchingTransactions.class, DbMatchingAdmission.class, MatchingMemberLockJpa.class, UserJpa.class, JpaAuditingConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchingConcurrencyTest {
    @Autowired PartyApplicationService service;
    @Autowired PartyJpa parties;
    @Autowired UserJpa users;
    @Autowired MatchingAdmission admission;
    @Autowired PlatformTransactionManager manager;
    @Autowired DataSource dataSource;
    @MockitoBean RouteFinder routeFinder;
    @MockitoBean RouteCache routeCache;
    @MockitoBean DriverAccess driverAccess;
    @MockitoBean UserAccess userAccess;
    @MockitoBean PartyCompletionPolicy completionPolicy;
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from active_match_participation");
        jdbc.update("delete from user_match_room");
        jdbc.update("delete from match_room");
        when(routeCache.find(any())).thenReturn(Optional.empty());
        when(routeFinder.find(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new RouteEstimate(12000, 25, "route");
        });
        when(userAccess.findMemberSummaries(any())).thenReturn(Map.of());
    }

    private Long user() { return users.save(User.from(UUID.randomUUID(), LoginType.LOCAL)).getId(); }
    private OpenPartyCommand command(Long user) {
        return new OpenPartyCommand(user, 37.4979, 127.0276, 37.3948, 127.1112, "강남역", "판교역", 2, 100, 100);
    }
    private Long room() { return service.open(command(user())).id(); }
    private long count(Long member) {
        return jdbc.queryForObject("select count(*) from active_match_participation where user_id = ?", Long.class, member);
    }
    private String outcome(Supplier<?> work) {
        try { work.get(); return "성공"; }
        catch (BusinessException e) { return e.getErrorCode().getCode(); }
    }
    private List<String> race(List<Supplier<?>> work) throws Exception {
        try (var pool = Executors.newFixedThreadPool(work.size())) {
            var start = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (var item : work) futures.add(pool.submit(() -> { start.await(); return outcome(item); }));
            start.countDown();
            List<String> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(15, TimeUnit.SECONDS));
            return results;
        }
    }

    @Test
    void 기존_조회와_방잠금만으로는_동일인의_두방_참가를_막지_못한다() throws Exception {
        Long member = user(), a = room(), b = room();
        var checked = new CyclicBarrier(2);
        // 변경 전 알고리즘을 SQL로 재현한다. 테스트에서만 새 참여 제약 테이블을 거치지 않는다.
        var results = race(List.of(() -> oldJoin(member, a, checked), () -> oldJoin(member, b, checked)));
        assertThat(results).containsExactly("성공", "성공");
        assertThat(jdbc.queryForObject("select count(*) from user_match_room where user_id = ?", Long.class, member)).isEqualTo(2);
    }
    private Object oldJoin(Long member, Long room, CyclicBarrier checked) {
        return new TransactionTemplate(manager).execute(status -> {
            assertThat(parties.existsOngoingByMemberId(member)).isFalse();
            await(checked);
            jdbc.queryForObject("select id from match_room where id = ? for update", Long.class, room);
            jdbc.update("insert into user_match_room(match_id, user_id, joined_at) values (?, ?, ?)",
                    room, member, java.sql.Timestamp.from(Instant.now()));
            return true;
        });
    }
    private static void await(CyclicBarrier barrier) {
        try { barrier.await(5, TimeUnit.SECONDS); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Test
    void 같은_사용자가_두방에_동시_참가하면_하나만_성공한다() throws Exception {
        Long member = user(), a = room(), b = room();
        assertThat(race(List.of(() -> service.join(a, member), () -> service.join(b, member))))
                .containsExactlyInAnyOrder("성공", ALREADY_JOINED_OTHER_PARTY.name());
        assertThat(count(member)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_match_room where user_id = ?", Long.class, member)).isEqualTo(1);
    }

    @Test
    void 같은_사용자의_방생성과_참가도_같은_잠금으로_보호한다() throws Exception {
        Long member = user(), a = room();
        assertThat(race(List.of(() -> service.open(command(member)), () -> service.join(a, member))))
                .containsExactlyInAnyOrder("성공", ALREADY_JOINED_OTHER_PARTY.name());
        assertThat(count(member)).isEqualTo(1);
    }

    @Test
    void 같은_사용자의_동시_방생성도_하나만_성공한다() throws Exception {
        Long member = user();
        assertThat(race(List.of(() -> service.open(command(member)), () -> service.open(command(member)))))
                .containsExactlyInAnyOrder("성공", ALREADY_JOINED_OTHER_PARTY.name());
        assertThat(count(member)).isEqualTo(1);
    }

    @Test
    void 마지막_한자리에_12명이_경쟁해도_정원을_초과하지_않는다() throws Exception {
        Long room = room();
        List<Supplier<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) { Long member = user(); tasks.add(() -> service.join(room, member)); }
        var results = race(tasks);
        assertThat(results.stream().filter("성공"::equals).count()).isEqualTo(1);
        assertThat(results).allMatch(r -> r.equals("성공") || r.equals(PARTY_CLOSED.name()) || r.equals(PARTY_FULL.name()));
        assertThat(service.getPartyDetail(room).currentMembers()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from active_match_participation where party_id = ?", Long.class, room)).isEqualTo(2);
    }

    @Test
    void 서로_다른_사용자는_전역_잠금으로_직렬화하지_않는다() throws Exception {
        var barrier = new CyclicBarrier(2);
        Long a = user(), b = user();
        assertThat(race(List.of(
                () -> admission.execute(a, () -> { await(barrier); return true; }),
                () -> admission.execute(b, () -> { await(barrier); return true; }))))
                .containsExactly("성공", "성공");
    }

    @Test
    void 저장_후_실패하면_현재참여와_방이_함께_롤백된다() {
        Long member = user();
        long before = jdbc.queryForObject("select count(*) from match_room", Long.class);
        assertThatThrownBy(() -> admission.execute(member, () -> {
            parties.save(Party.open(member, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112),
                    "강남역", "판교역", new Capacity(2), Instant.now(), new Radius(100), new Radius(100), 12000, 25, "route"));
            throw new IllegalStateException("저장 후 실패 재현");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count(member)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from match_room", Long.class)).isEqualTo(before);
        assertThat(service.open(command(member))).isNotNull();
    }

    @Test
    void 반복_탈퇴와_재참가_및_종료후_재매칭을_허용한다() {
        Long room = room(), member = user();
        for (int i = 0; i < 3; i++) {
            service.join(room, member);
            service.leave(room, member);
            assertThat(count(member)).isZero();
        }
        service.join(room, member);
        service.finish(room, member);
        assertThat(count(member)).isZero();
        assertThat(service.open(command(member))).isNotNull();
        // 종료된 방의 이력은 여전히 남는다.
        assertThat(jdbc.queryForObject("select count(*) from user_match_room where user_id = ?", Long.class, member)).isEqualTo(2);
    }

    @Test
    void 잠금이_겹쳐도_DB_PK가_두계정이_아닌_동일사용자의_중복참여를_최종_차단한다() throws Exception {
        Long member = user(), a = room(), b = room();
        var barrier = new CyclicBarrier(2);
        // Redis 락 만료/소유권 상실로 두 작업이 진입한 상황에 해당하는 DB 방어 시험.
        assertThat(race(List.of(() -> withoutUserLock(a, member, barrier), () -> withoutUserLock(b, member, barrier))))
                .containsExactlyInAnyOrder("성공", ALREADY_JOINED_OTHER_PARTY.name());
        assertThat(count(member)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_match_room where user_id = ?", Long.class, member)).isEqualTo(1);
    }
    private Object withoutUserLock(Long room, Long member, CyclicBarrier barrier) {
        return new TransactionTemplate(manager).execute(status -> {
            assertThat(parties.existsOngoingByMemberId(member)).isFalse();
            await(barrier);
            Party party = parties.findByIdForUpdate(room).orElseThrow();
            party.join(member);
            return parties.save(party);
        });
    }

    @Test
    void 상위_트랜잭션이_있으면_잠금보다_먼저_거절한다() {
        Long member = user();
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> admission.execute(member, () -> true)))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test
    void DB_사용자_락은_2초_대기후_실패하고_입장작업을_실행하지_않는다() throws Exception {
        Long member = user();
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var holder = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                jdbc.queryForObject("select id from users where id = ? for update", Long.class, member);
                held.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("해제 대기 초과"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                return true;
            }));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> admission.execute(member, () -> { throw new AssertionError("작업 실행 금지"); }))
                        .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(MATCHING_BUSY);
            } finally { release.countDown(); }
            holder.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "MATCHING_BENCHMARK", matches = "true")
    void 동일조건_성능_비교() throws Exception {
        // 외부 API는 위 fixture처럼 고정 응답. 네트워크/HTTP 성능 시험이 아니다.
        for (int i = 0; i < 20; i++) service.join(room(), user());
        for (int round = 1; round <= 3; round++) {
            for (String scenario : List.of("동일사용자", "한방집중", "분산참가")) {
                int n = 96;
                Long sharedMember = user(), sharedRoom = room();
                List<Supplier<?>> work = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    Long member = scenario.equals("동일사용자") ? sharedMember : user();
                    Long target = scenario.equals("한방집중") ? sharedRoom : room();
                    work.add(() -> service.join(target, member));
                }
                var durations = new java.util.concurrent.ConcurrentLinkedQueue<Long>();
                var successes = new java.util.concurrent.atomic.AtomicInteger();
                var rejections = new java.util.concurrent.atomic.AtomicInteger();
                var peakConnections = new java.util.concurrent.atomic.AtomicInteger();
                var hikari = dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class);
                long elapsed;
                try (var pool = Executors.newFixedThreadPool(16);
                     var sampler = Executors.newSingleThreadScheduledExecutor()) {
                    var start = new CountDownLatch(1);
                    List<Future<?>> futures = new ArrayList<>();
                    for (var item : work) futures.add(pool.submit(() -> {
                        try { start.await(); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                        long began = System.nanoTime();
                        String result = outcome(item);
                        durations.add(System.nanoTime() - began);
                        if (result.equals("성공")) successes.incrementAndGet(); else rejections.incrementAndGet();
                    }));
                    sampler.scheduleAtFixedRate(() -> peakConnections.accumulateAndGet(
                            hikari.getHikariPoolMXBean().getActiveConnections(), Math::max), 0, 5, TimeUnit.MILLISECONDS);
                    long began = System.nanoTime(); start.countDown();
                    for (var future : futures) future.get(30, TimeUnit.SECONDS);
                    elapsed = System.nanoTime() - began;
                    sampler.shutdownNow();
                }
                assertThat(successes.get()).isEqualTo(scenario.equals("분산참가") ? n : 1);
                long[] sorted = durations.stream().mapToLong(Long::longValue).sorted().toArray();
                System.out.printf(Locale.ROOT,
                        "BENCHMARK strategy=%s scenario=%s round=%d requests=%d success=%d rejected=%d p50_ms=%.3f p95_ms=%.3f rps=%.1f peak_connections=%d%n",
                        admission.getClass().getSimpleName(), scenario, round, n, successes.get(), rejections.get(),
                        sorted[n / 2] / 1e6, sorted[(int) Math.ceil(n * .95) - 1] / 1e6, n / (elapsed / 1e9), peakConnections.get());
            }
        }
    }

}
