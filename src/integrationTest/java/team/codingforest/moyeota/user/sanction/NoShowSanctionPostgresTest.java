package team.codingforest.moyeota.user.sanction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.user.api.NoShowPenalty;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  users 행의 노쇼 제재 컬럼이 실제 Postgres 에서 잃어버려지지 않는지 본다 -
 *  같은 사용자의 노쇼가 동시에 더해질 때(행 잠금), 프로필 수정이 읽어 둔 옛 값으로 제재를 덮어쓸 때(@DynamicUpdate)
 */
@SpringBootTest(properties = {"spring.profiles.active=dev", "moyeota.taxi.enabled=false"})
class NoShowSanctionPostgresTest {
    private static final Instant MEETING_AT = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofMinutes(10));

    @Autowired NoShowPenalty noShowPenalty;
    @Autowired Users users;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> created = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        created.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
    }

    @Test
    void 같은_사용자의_노쇼가_동시에_더해져도_하나도_빠지지_않는다() throws Exception {
        Long userId = newUser();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> record = () -> {
            start.await();
            noShowPenalty.record(userId, MEETING_AT, Instant.now());
            return null;
        };

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) futures.add(pool.submit(record));
        start.countDown();
        for (Future<Void> future : futures) future.get();

        assertThat(count(userId)).isEqualTo(4);
        assertThat(restrictedUntil(userId)).as("약속 시간부터 4 × 24시간").isEqualTo(MEETING_AT.plus(Duration.ofHours(96)));
    }

    @Test
    void 프로필_수정이_읽어_둔_옛_값으로_노쇼_제재를_덮어쓰지_않는다() {
        Long userId = newUser();
        String nickname = "nk" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
        TransactionTemplate profileEdit = new TransactionTemplate(transactionManager);
        TransactionTemplate noShow = new TransactionTemplate(transactionManager);
        noShow.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        profileEdit.executeWithoutResult(status -> {
            User user = users.findById(userId);   // 프로필 수정이 행을 읽어 둔다(제재 0회)
            noShow.executeWithoutResult(s -> noShowPenalty.record(userId, MEETING_AT, Instant.now()));   // 그사이 출발 트랜잭션이 노쇼를 더해 커밋
            user.updateProfile(nickname, null);
            users.save(user);
        });

        assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, userId)).isEqualTo(nickname);
        assertThat(count(userId)).as("바뀐 컬럼만 UPDATE 해야 노쇼가 살아남는다").isEqualTo(1);
        assertThat(restrictedUntil(userId)).isEqualTo(MEETING_AT.plus(Duration.ofHours(24)));
    }

    @Test
    void 기록할_때_30일_감소를_반영해_저장하고_인정되면_1_줄이며_제한을_푼다() {
        Long userId = newUser();
        jdbc.update("UPDATE users SET no_show_count = 3, no_show_count_updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(61))), userId);

        noShowPenalty.record(userId, MEETING_AT, Instant.now());

        assertThat(count(userId)).as("3회에서 61일 지나 1회 + 이번 1회").isEqualTo(2);
        assertThat(restrictedUntil(userId)).isEqualTo(MEETING_AT.plus(Duration.ofHours(48)));
        assertThat(noShowPenalty.findRestrictedUntil(userId, Instant.now())).contains(MEETING_AT.plus(Duration.ofHours(48)));

        noShowPenalty.cancel(userId, Instant.now());

        assertThat(count(userId)).isEqualTo(1);
        assertThat(restrictedUntil(userId)).isNull();
        assertThat(noShowPenalty.findRestrictedUntil(userId, Instant.now())).isEmpty();
    }

    @Test
    void 없는_사용자는_기록과_취소를_해도_예외_없이_넘어가고_제한도_없다() {
        Long ghost = -ThreadLocalRandom.current().nextLong(1, 1_000_000);

        noShowPenalty.record(ghost, MEETING_AT, Instant.now());
        noShowPenalty.cancel(ghost, Instant.now());

        assertThat(noShowPenalty.findRestrictedUntil(ghost, Instant.now())).isEmpty();
    }

    private Long newUser() {
        Long id = jdbc.queryForObject("INSERT INTO users (public_id, login_type, created_at) VALUES (?, 'LOCAL', now()) RETURNING id",
                Long.class, UUID.randomUUID());
        created.add(id);
        return id;
    }

    private int count(Long userId) {
        return jdbc.queryForObject("SELECT no_show_count FROM users WHERE id = ?", Integer.class, userId);
    }

    private Instant restrictedUntil(Long userId) {
        Timestamp value = jdbc.queryForObject("SELECT restricted_until FROM users WHERE id = ?", Timestamp.class, userId);
        return value == null ? null : value.toInstant();
    }
}
