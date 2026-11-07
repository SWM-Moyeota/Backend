package team.codingforest.moyeota.user.sanction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.user.api.NoShowPenalty;
import team.codingforest.moyeota.user.local.application.LocalUserService;
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
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  노쇼 제재가 실제 Postgres 에서 잃어버려지지 않는지 본다 - 같은 사용자의 노쇼가 동시에 기록될 때(users 행 잠금),
 *  같은 방이 두 번 기록될 때(user_no_show 유니크), 프로필 수정이 읽어 둔 옛 값으로 제한을 덮어쓸 때(@DynamicUpdate).
 *  방 번호는 match_room 을 가리키지 않으므로(FK 없음) 아무 번호나 쓴다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class NoShowSanctionPostgresTest {
    private static final Instant MEETING_AT = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofMinutes(10));
    private static final Duration DAY = Duration.ofHours(24);

    @Autowired NoShowPenalty noShowPenalty;
    @Autowired LocalUserService localUserService;
    @Autowired Users users;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> created = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        created.forEach(id -> {
            jdbc.update("DELETE FROM user_no_show WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        });
    }

    @DisplayName("record - 같은 사용자의 다른 방 노쇼가 동시에 기록돼도 서로를 세어 제한이 4 × 24시간이 된다")
    @Test
    void record_concurrentDifferentParties() throws Exception {

        /*
         * settings
         */
        Long userId = newUser();
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> records = new ArrayList<>();
        for (long partyId = 1; partyId <= 4; partyId++) {
            long party = partyId;
            records.add(() -> {
                start.await();
                noShowPenalty.record(userId, party, MEETING_AT, Instant.now());
                return null;
            });
        }

        /*
         * test
         */
        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> record : records) futures.add(pool.submit(record));
        start.countDown();
        for (Future<Void> future : futures) future.get();

        /*
         * verify
         */
        assertThat(rows(userId)).isEqualTo(4);
        assertThat(restrictedUntil(userId)).as("약속 시간부터 4 × 24시간").isEqualTo(MEETING_AT.plus(DAY.multipliedBy(4)));
        assertThat(localUserService.getProfile(userId).noShowCount()).isEqualTo(4);
    }

    @DisplayName("record - 같은 방의 노쇼가 동시에 여러 번 기록돼도 한 번만 세고 예외 없이 끝난다")
    @Test
    void record_concurrentSameParty() throws Exception {

        /*
         * settings
         */
        Long userId = newUser();
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> record = () -> {
            start.await();
            noShowPenalty.record(userId, 1L, MEETING_AT, Instant.now());
            return null;
        };

        /*
         * test
         */
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) futures.add(pool.submit(record));
        start.countDown();
        for (Future<Void> future : futures) future.get();

        /*
         * verify
         */
        assertThat(rows(userId)).isEqualTo(1);
        assertThat(restrictedUntil(userId)).isEqualTo(MEETING_AT.plus(DAY));
    }

    @DisplayName("record - 같은 방을 다시 기록해도 두 번 세지 않고 제한도 그대로다")
    @Test
    void record_samePartyTwice() {

        /*
         * settings
         */
        Long userId = newUser();
        noShowPenalty.record(userId, 1L, MEETING_AT, Instant.now());

        /*
         * test
         */
        noShowPenalty.record(userId, 1L, MEETING_AT.plusSeconds(60), Instant.now());

        /*
         * verify
         */
        assertThat(rows(userId)).isEqualTo(1);
        assertThat(restrictedUntil(userId)).isEqualTo(MEETING_AT.plus(DAY));
    }

    @DisplayName("user_no_show - 같은 사용자·방을 직접 두 번 넣으면 유니크 제약이 막는다")
    @Test
    void uniqueUserAndParty() {

        /*
         * settings
         */
        Long userId = newUser();
        noShowPenalty.record(userId, 1L, MEETING_AT, Instant.now());

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> jdbc.update(
                "INSERT INTO user_no_show (user_id, match_id, meeting_at, created_at) VALUES (?, 1, ?, now())", userId, Timestamp.from(MEETING_AT)));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rows(userId)).isEqualTo(1);
    }

    @DisplayName("record·cancel - 30일이 지난 노쇼와 무효로 돌린 노쇼는 세지 않고, 취소하면 그 방의 노쇼가 무효가 되고 제한이 풀린다")
    @Test
    void recordAndCancel() {

        /*
         * settings
         */
        Long userId = newUser();
        insertNoShow(userId, 1L, Instant.now().minus(Duration.ofDays(31)));   // 만료
        insertNoShow(userId, 2L, Instant.now().minus(Duration.ofDays(10)));

        /*
         * test & verify
         */
        noShowPenalty.record(userId, 3L, MEETING_AT, Instant.now());
        assertThat(restrictedUntil(userId)).as("10일 전 1건 + 이번 1건").isEqualTo(MEETING_AT.plus(DAY.multipliedBy(2)));
        assertThat(noShowPenalty.findRestrictedUntil(userId, Instant.now())).contains(MEETING_AT.plus(DAY.multipliedBy(2)));
        assertThat(localUserService.getProfile(userId).noShowCount()).isEqualTo(2);

        noShowPenalty.cancel(userId, 3L, Instant.now());
        assertThat(voidedAt(userId, 3L)).isNotNull();
        assertThat(voidedAt(userId, 2L)).isNull();
        assertThat(restrictedUntil(userId)).isNull();
        assertThat(noShowPenalty.findRestrictedUntil(userId, Instant.now())).isEmpty();
        assertThat(localUserService.getProfile(userId).noShowCount()).isEqualTo(1);

        noShowPenalty.record(userId, 4L, MEETING_AT, Instant.now());
        assertThat(restrictedUntil(userId)).as("무효로 돌린 3번 방은 빼고 2번·4번 방").isEqualTo(MEETING_AT.plus(DAY.multipliedBy(2)));
    }

    @DisplayName("cancel - 그 방의 노쇼가 없으면 아무것도 바꾸지 않고, 이미 무효면 처음 무효 시각을 둔다")
    @Test
    void cancel_unknownOrAlreadyVoided() {

        /*
         * settings
         */
        Long userId = newUser();
        noShowPenalty.record(userId, 1L, MEETING_AT, Instant.now());
        Instant until = restrictedUntil(userId);

        /*
         * test & verify
         */
        noShowPenalty.cancel(userId, 2L, Instant.now());
        assertThat(restrictedUntil(userId)).isEqualTo(until);
        assertThat(voidedAt(userId, 1L)).isNull();

        noShowPenalty.cancel(userId, 1L, Instant.now());
        Instant voidedAt = voidedAt(userId, 1L);
        noShowPenalty.cancel(userId, 1L, Instant.now().plusSeconds(60));
        assertThat(voidedAt(userId, 1L)).isEqualTo(voidedAt);
    }

    @DisplayName("users - 프로필 수정이 읽어 둔 옛 값으로 노쇼 제한을 덮어쓰지 않는다")
    @Test
    void profileEditDoesNotOverwriteRestriction() {

        /*
         * settings
         */
        Long userId = newUser();
        String nickname = "nk" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
        TransactionTemplate profileEdit = new TransactionTemplate(transactionManager);
        TransactionTemplate noShow = new TransactionTemplate(transactionManager);
        noShow.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        /*
         * test
         */
        profileEdit.executeWithoutResult(status -> {
            User user = users.findById(userId);   // 프로필 수정이 행을 읽어 둔다(제한 없음)
            noShow.executeWithoutResult(s -> noShowPenalty.record(userId, 1L, MEETING_AT, Instant.now()));   // 그사이 출발 트랜잭션이 노쇼를 기록해 커밋
            user.updateProfile(nickname, null);
            users.save(user);
        });

        /*
         * verify
         */
        assertThat(jdbc.queryForObject("SELECT nickname FROM users WHERE id = ?", String.class, userId)).isEqualTo(nickname);
        assertThat(restrictedUntil(userId)).as("바뀐 컬럼만 UPDATE 해야 제한이 살아남는다").isEqualTo(MEETING_AT.plus(DAY));
    }

    @DisplayName("record·cancel - 없는 사용자는 예외 없이 넘어가고 노쇼도 남기지 않는다")
    @Test
    void unknownUser() {

        /*
         * settings
         */
        Long ghost = -ThreadLocalRandom.current().nextLong(1, 1_000_000);

        /*
         * test
         */
        noShowPenalty.record(ghost, 1L, MEETING_AT, Instant.now());
        noShowPenalty.cancel(ghost, 1L, Instant.now());

        /*
         * verify
         */
        assertThat(rows(ghost)).isZero();
        assertThat(noShowPenalty.findRestrictedUntil(ghost, Instant.now())).isEmpty();
    }

    private Long newUser() {
        Long id = jdbc.queryForObject("INSERT INTO users (public_id, login_type, created_at) VALUES (?, 'LOCAL', now()) RETURNING id",
                Long.class, UUID.randomUUID());
        created.add(id);
        return id;
    }

    private void insertNoShow(Long userId, Long partyId, Instant meetingAt) {
        jdbc.update("INSERT INTO user_no_show (user_id, match_id, meeting_at, created_at) VALUES (?, ?, ?, now())",
                userId, partyId, Timestamp.from(meetingAt));
    }

    private int rows(Long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM user_no_show WHERE user_id = ?", Integer.class, userId);
    }

    private Instant voidedAt(Long userId, Long partyId) {
        return instant(jdbc.queryForObject("SELECT voided_at FROM user_no_show WHERE user_id = ? AND match_id = ?",
                Timestamp.class, userId, partyId));
    }

    private Instant restrictedUntil(Long userId) {
        return instant(jdbc.queryForObject("SELECT restricted_until FROM users WHERE id = ?", Timestamp.class, userId));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
