package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;
import team.codingforest.moyeota.user.api.UserDeletedEvent;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 *  택시비 정산 스케줄러를 실제 Postgres 에서 본다 - 재요청 대상 조회가 마지막 송금 요청 시각(timestamptz) 4시간 뒤를 가르는지, 재요청·입금 확인 요청·만료가 컬럼에 남는지,
 *  만료·받는 사람 탈퇴로 계좌번호 컬럼이 NULL 이 되는지, 재요청이 정산 행 잠금으로 보냈어요와 줄 서 보낸 시각을 잃지 않는지.
 *  실제 스케줄러(1분마다 지금 시각으로 돈다)가 테스트 행을 고르지 않게 기준 시각을 30일 뒤로 잡는다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class FareSplitSweepPostgresTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 다른 테스트가 남긴 방과 섞이지 않게 대전 좌표를 쓴다
    private static final Location DEPARTURE = new Location(36.3504, 127.3845);
    private static final Location DESTINATION = new Location(36.3622, 127.3568);
    private static final String RAW_ACCOUNT = "3333-01-2345678";
    private static final LocalDate DAY = LocalDate.now(SEOUL).plusDays(30);
    private static final Instant NOON = at(DAY, 12, 0);

    @Autowired FareSplitService service;
    @Autowired FareSplitNotificationService notificationService;
    @Autowired FareSplits fareSplits;
    @Autowired Parties parties;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationEventPublisher publisher;

    // 다른 테스트·이전 실행과 섞이지 않게 매번 새 번호를 쓴다(users 행은 필요 없다 - 정산은 users 에 FK 를 걸지 않는다)
    private final Long 받는사람 = ThreadLocalRandom.current().nextLong(8_000_000_000L, 9_000_000_000L);
    private final Long 첫째 = 받는사람 + 1;
    private final Long 둘째 = 받는사람 + 2;
    private final List<Long> rooms = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        rooms.forEach(id -> {
            jdbc.update("DELETE FROM fare_split_transfer WHERE match_id = ?", id);
            jdbc.update("DELETE FROM fare_split WHERE match_id = ?", id);
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    // ───────────────────────── 대상 조회 ─────────────────────────

    @Test
    void 재요청_대상은_마지막_송금_요청_4시간_뒤를_가른다() {
        Long 차례 = startedSplit(NOON.minus(Duration.ofHours(4)));
        Long 차례전 = startedSplit(NOON.minus(Duration.ofHours(4)).plusSeconds(1));
        // 시작이 아니라 마지막 요청부터 센다
        Long 최근에요청함 = startedSplit(NOON.minus(Duration.ofHours(20)));
        setRequestedAt(최근에요청함, NOON.minus(Duration.ofHours(1)));

        List<Long> targets = fareSplits.findTransferRequestTargets(NOON);

        assertThat(targets).contains(차례).doesNotContain(차례전, 최근에요청함);
    }

    @Test
    void 입금_확인_요청과_만료_대상은_경계_시각을_포함한다() {
        Long 확인요청 = startedSplit(NOON.minus(Duration.ofHours(5)));
        jdbc.update("UPDATE fare_split_transfer SET sent_at = ? WHERE match_id = ?", Timestamp.from(NOON.minus(Duration.ofHours(4))), 확인요청);
        Long 확인요청전 = startedSplit(NOON.minus(Duration.ofHours(5)));
        jdbc.update("UPDATE fare_split_transfer SET sent_at = ? WHERE match_id = ?", Timestamp.from(NOON.minus(Duration.ofHours(4)).plusSeconds(1)), 확인요청전);
        Long 만료 = startedSplit(NOON.minus(Duration.ofDays(1)));
        Long 만료전 = startedSplit(NOON.minus(Duration.ofDays(1)).plusSeconds(1));

        assertThat(fareSplits.findConfirmRequestTargets(NOON)).contains(확인요청).doesNotContain(확인요청전, 만료, 만료전);
        assertThat(fareSplits.findExpireTargets(NOON)).contains(만료).doesNotContain(만료전, 확인요청, 확인요청전);
    }

    // ───────────────────────── 재요청·입금 확인 요청 ─────────────────────────

    @Test
    void 재요청은_안_보낸_사람의_요청_시각만_남기고_다시_불러도_그대로다() {
        Long room = finishedRoom(받는사람, 첫째, 둘째);
        service.create(room, 받는사람, 18_700, Bank.KAKAO, RAW_ACCOUNT);
        service.markSent(room, 둘째);
        startedAt(room, NOON.minus(Duration.ofHours(4)));

        Instant beforeNext = NOON.plus(FareSplit.REMIND_AFTER).minusSeconds(1);

        notificationService.remindTransfers(room, NOON);
        notificationService.remindTransfers(room, beforeNext);

        assertThat(lastRequestedAt(room, 첫째).toInstant()).as("4시간 안에 다시 불러도 다시 보내지 않는다").isEqualTo(NOON);
        assertThat(lastRequestedAt(room, 둘째).toInstant()).as("보냈다고 누른 사람은 재요청하지 않는다").isEqualTo(NOON.minus(Duration.ofHours(4)));
        assertThat(fareSplits.findTransferRequestTargets(beforeNext)).doesNotContain(room);
    }

    @Test
    void 입금_확인_요청은_보냄_4시간_뒤_새벽에도_한_번만_남는다() {
        Long room = finishedRoom(받는사람, 첫째);
        service.create(room, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
        setCreatedAt(room, at(DAY, 1, 0));
        // 새벽 2시에 보냈다고 눌러 새벽 6시가 입금 확인 요청 차례다
        jdbc.update("UPDATE fare_split_transfer SET sent_at = ? WHERE match_id = ?", Timestamp.from(at(DAY, 2, 0)), room);

        notificationService.requestConfirm(room, at(DAY, 6, 0).minusSeconds(1));
        assertThat(confirmRequestedAt(room, 첫째)).as("4시간 전").isNull();

        notificationService.requestConfirm(room, at(DAY, 6, 0));
        notificationService.requestConfirm(room, at(DAY, 7, 0));

        assertThat(confirmRequestedAt(room, 첫째).toInstant()).isEqualTo(at(DAY, 6, 0));
        assertThat(fareSplits.findConfirmRequestTargets(at(DAY, 7, 0))).doesNotContain(room);
    }

    // ───────────────────────── 스케줄러 한 바퀴 ─────────────────────────

    @Test
    void 스케줄러는_정산마다_따로_커밋해_만료하고_재요청하고_입금_확인을_요청한다() {
        Long 만료방 = finishedRoom(받는사람, 첫째);
        service.create(만료방, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
        startedAt(만료방, NOON.minus(Duration.ofDays(3)));
        Long 재요청방 = finishedRoom(받는사람, 첫째);
        service.create(재요청방, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
        startedAt(재요청방, NOON.minus(Duration.ofHours(4)));
        Long 확인요청방 = finishedRoom(받는사람, 첫째);
        service.create(확인요청방, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
        startedAt(확인요청방, NOON.minus(Duration.ofHours(5)));
        jdbc.update("UPDATE fare_split_transfer SET sent_at = ? WHERE match_id = ?", Timestamp.from(NOON.minus(Duration.ofHours(4))), 확인요청방);
        // 다른 테스트가 남긴 정산을 건드리지 않게 실제 쿼리 결과 중 이 테스트의 방만 넘긴다. 처리는 실제(프록시) 서비스가 한다
        FareSplitSweeper sweeper = new FareSplitSweeper(onlyMyRooms(), service, notificationService);

        sweeper.sweepAt(NOON);

        assertThat(jdbc.queryForObject("SELECT status FROM fare_split WHERE match_id = ?", String.class, 만료방)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT closed_at FROM fare_split WHERE match_id = ?", Timestamp.class, 만료방).toInstant()).isEqualTo(NOON);
        assertThat(jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, 만료방)).isNull();
        assertThat(lastRequestedAt(만료방, 첫째).toInstant()).as("만료를 먼저 해 닫힌 정산은 재요청하지 않는다").isEqualTo(NOON.minus(Duration.ofDays(3)));
        assertThat(lastRequestedAt(재요청방, 첫째).toInstant()).isEqualTo(NOON);
        assertThat(confirmRequestedAt(확인요청방, 첫째).toInstant()).isEqualTo(NOON);
        assertThat(jdbc.queryForObject("SELECT status FROM fare_split WHERE match_id = ?", String.class, 재요청방)).isEqualTo("OPEN");
    }

    // ───────────────────────── 받는 사람 탈퇴 ─────────────────────────

    @Test
    void 받는_사람이_탈퇴하면_그_사람의_OPEN_정산만_계좌번호_컬럼이_NULL_이_되고_정산은_열려_있다() {
        Long 내정산 = finishedRoom(받는사람, 첫째);
        service.create(내정산, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
        Long 남의정산 = finishedRoom(첫째, 받는사람);
        service.create(남의정산, 첫째, 10_000, Bank.KB, RAW_ACCOUNT);

        tx.executeWithoutResult(status -> publisher.publishEvent(new UserDeletedEvent(받는사람, UUID.randomUUID())));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, 내정산)).isNull());
        assertThat(jdbc.queryForObject("SELECT status FROM fare_split WHERE match_id = ?", String.class, 내정산)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, 남의정산))
                .as("보내는 사람으로 있던 정산의 계좌는 남의 것이라 그대로").startsWith("v1:");
        assertThat(service.getDetail(내정산, 첫째).accountNumber()).isNull();
        assertThat(service.getDetail(내정산, 첫째).tossDeeplink()).isNull();
        assertThat(service.forgetPayerAccounts(받는사람)).as("다시 불러도 된다").isZero();
        assertThat(fareSplits.findTransferRequestTargets(NOON.plus(Duration.ofDays(3)))).as("받을 계좌가 없어 재요청하지 않는다").doesNotContain(내정산);
    }

    // ───────────────────────── 정산 행 잠금 ─────────────────────────

    @Test
    void 재요청은_정산_행_잠금을_기다렸다가_돈다() throws Exception {
        Long room = finishedRoom(받는사람, 첫째);
        service.create(room, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
        startedAt(room, NOON.minus(Duration.ofHours(4)));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
            fareSplits.findByPartyIdForUpdate(room).orElseThrow();
            locked.countDown();
            awaitQuietly(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        Future<?> reminder = pool.submit(() -> notificationService.remindTransfers(room, NOON));
        await().atMost(Duration.ofSeconds(5))
                .until(() -> jdbc.queryForObject("""
                        select count(*) from pg_stat_activity
                        where datname = current_database() and wait_event_type = 'Lock' and query like '%fare_split%for no key update%'
                        """, Integer.class) > 0);
        assertThat(reminder.isDone()).as("정산 잠금이 풀리기 전에는 바꾸지 못한다").isFalse();

        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        reminder.get(10, TimeUnit.SECONDS);
        assertThat(lastRequestedAt(room, 첫째).toInstant()).isEqualTo(NOON);
    }

    @Test
    void 재요청과_보냈어요가_동시에_와도_보낸_시각을_잃지_않는다() throws Exception {
        // 잠금이 없으면 재요청이 앞서 읽은 송금 행 전체를 덮어써 보낸 시각을 잃는다 - 여러 방에서 되풀이해 경합을 만든다
        for(int i = 0; i < 3; i++) {
            Long room = finishedRoom(받는사람, 첫째);
            service.create(room, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
            startedAt(room, NOON.minus(Duration.ofHours(4)));
            CountDownLatch start = new CountDownLatch(1);

            Future<?> sent = pool.submit(() -> {
                start.await();
                service.markSent(room, 첫째);
                return null;
            });
            Future<?> reminder = pool.submit(() -> {
                start.await();
                notificationService.remindTransfers(room, NOON);
                return null;
            });
            start.countDown();
            sent.get(20, TimeUnit.SECONDS);
            reminder.get(20, TimeUnit.SECONDS);

            assertThat(jdbc.queryForObject("SELECT sent_at FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", Timestamp.class, room, 첫째))
                    .isNotNull();
        }
    }

    // ───────────────────────── fixtures ─────────────────────────

    /** 받는 사람·첫째가 탄 방에 startedAt 에 시작한 정산 */
    private Long startedSplit(Instant startedAt) {
        Long room = finishedRoom(받는사람, 첫째);
        service.create(room, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
        startedAt(room, startedAt);

        return room;
    }

    /** 넘긴 순서대로 합류하고 모두 도착해 출발한(FINISHED) 방 */
    private Long finishedRoom(Long... memberIdsInJoinOrder) {
        Instant meetingAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofHours(1));
        List<PartyMember> members = new ArrayList<>();
        for(Long memberId : memberIdsInJoinOrder) {
            members.add(PartyMember.load(memberId, meetingAt, meetingAt));
        }

        Long id = parties.save(Party.load(null, DEPARTURE, DESTINATION, "대전역", "시청역", new Capacity(3), members,
                memberIdsInJoinOrder[0], meetingAt.minus(Duration.ofHours(1)), PartyStatus.FINISHED,
                12000, 25, "encoded-path", 0L, RoomType.REALTIME, meetingAt)).getId();
        rooms.add(id);

        for(int i = 0; i < memberIdsInJoinOrder.length; i++) {
            jdbc.update("UPDATE user_match_room SET created_at = ? WHERE match_id = ? AND user_id = ?",
                    Timestamp.from(meetingAt.minusSeconds(60 - i * 10L)), id, memberIdsInJoinOrder[i]);
        }

        return id;
    }

    /** 부를 때마다 실제 대상 조회를 돌려 그중 이 테스트가 만든 방만 돌려주는 FareSplits(스케줄러는 대상 조회만 쓴다) */
    private FareSplits onlyMyRooms() {
        FareSplits targets = mock(FareSplits.class);
        doAnswer(inv -> mine(fareSplits.findExpireTargets(inv.getArgument(0)))).when(targets).findExpireTargets(any());
        doAnswer(inv -> mine(fareSplits.findTransferRequestTargets(inv.getArgument(0)))).when(targets).findTransferRequestTargets(any());
        doAnswer(inv -> mine(fareSplits.findConfirmRequestTargets(inv.getArgument(0)))).when(targets).findConfirmRequestTargets(any());

        return targets;
    }

    private List<Long> mine(List<Long> partyIds) {
        return partyIds.stream().filter(rooms::contains).toList();
    }

    private void setCreatedAt(Long room, Instant createdAt) {
        jdbc.update("UPDATE fare_split SET created_at = ? WHERE match_id = ?", Timestamp.from(createdAt), room);
    }

    /** 정산을 startedAt 에 시작하고 그때 첫 송금 요청을 보낸 것으로 맞춘다 */
    private void startedAt(Long room, Instant startedAt) {
        setCreatedAt(room, startedAt);
        setRequestedAt(room, startedAt);
    }

    private void setRequestedAt(Long room, Instant requestedAt) {
        jdbc.update("UPDATE fare_split_transfer SET last_requested_at = ? WHERE match_id = ?", Timestamp.from(requestedAt), room);
    }

    private Timestamp lastRequestedAt(Long room, Long userId) {
        return jdbc.queryForObject("SELECT last_requested_at FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", Timestamp.class, room, userId);
    }

    private Timestamp confirmRequestedAt(Long room, Long userId) {
        return jdbc.queryForObject("SELECT confirm_requested_at FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", Timestamp.class, room, userId);
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(SEOUL).toInstant();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if(!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release 대기 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
