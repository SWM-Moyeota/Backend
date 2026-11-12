package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.FarePayment;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotifier;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.faresplit.dto.FareSplitRequest;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 *  택시비 정산 스케줄러를 실제 Postgres 에서 본다 - 재요청 대상 조회가 정산 시작 시각(timestamptz) + 요청 횟수 × 4시간을 가르는지, 재요청 횟수·만료가 컬럼에 남는지,
 *  받는 사람(방장)의 줄(처음부터 CONFIRMED·요청 0번) 때문에 재요청 대상·알림·요청 횟수가 달라지지 않는지,
 *  만료·받는 사람 탈퇴로 계좌번호 컬럼이 NULL 이 되는지, 재요청이 정산 행 잠금으로 보냈어요와 줄 서 보낸 시각을 잃지 않는지.
 *  실제 스케줄러(30분마다 지금 시각으로 돈다)가 테스트 행을 고르지 않게 기준 시각을 30일 뒤로 잡는다
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
    // 실제 알림 구현을 그대로 두고 호출만 본다
    @MockitoSpyBean FareSplitNotifier notifier;

    // 정산 시작은 받는 사람(방장)을 포함한 탑승자를 publicId 로 받는다 - 다른 테스트·이전 실행과 섞이지 않게 매번 실제 users 행을 새로 만든다
    private Long 받는사람;
    private Long 첫째;
    private Long 둘째;
    private final List<Long> rooms = new ArrayList<>();
    private final List<Long> users = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @BeforeEach
    void setUp() {
        받는사람 = newUser();
        첫째 = newUser();
        둘째 = newUser();
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        rooms.forEach(id -> {
            jdbc.update("DELETE FROM fare_split_transfer WHERE match_id = ?", id);
            jdbc.update("DELETE FROM fare_split WHERE match_id = ?", id);
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
        users.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
    }

    // ───────────────────────── 대상 조회 ─────────────────────────

    @Test
    void 재요청_대상은_정산_시작_시각에_요청_횟수만큼_4시간을_더한_차례를_가른다() {
        Long 차례 = startedSplit(NOON.minus(Duration.ofHours(4)));
        Long 차례전 = startedSplit(NOON.minus(Duration.ofHours(4)).plusSeconds(1));
        // 요청을 이미 2번 받았으면 다음 차례는 시작 8시간 뒤다
        Long 두번받고차례 = startedSplit(NOON.minus(Duration.ofHours(8)));
        setRequestCount(두번받고차례, 2);
        Long 두번받고차례전 = startedSplit(NOON.minus(Duration.ofHours(8)).plusSeconds(1));
        setRequestCount(두번받고차례전, 2);
        // 차례가 여러 번 밀렸어도 대상이다
        Long 밀린차례 = startedSplit(NOON.minus(Duration.ofHours(20)));
        Long 다섯번받고차례 = startedSplit(NOON.minus(Duration.ofHours(20)));
        setRequestCount(다섯번받고차례, 5);
        // 첫 요청 1번과 재요청 5번을 다 보냈다
        Long 여섯번받음 = startedSplit(NOON.minus(Duration.ofHours(23)));
        setRequestCount(여섯번받음, FareSplit.MAX_TRANSFER_REQUESTS);

        List<Long> targets = fareSplits.findTransferRequestTargets(NOON);

        assertThat(targets).contains(차례, 두번받고차례, 밀린차례, 다섯번받고차례).doesNotContain(차례전, 두번받고차례전, 여섯번받음);
    }

    @Test
    void 재요청_대상은_보냈거나_확인된_송금을_세지_않고_요청_횟수가_다른_송금이_함께_차례여도_방을_한_번만_방_번호_순으로_준다() {
        Instant 여덟시간전 = NOON.minus(Duration.ofHours(8));
        // 첫째는 2번, 둘째는 1번 받아 둘 다 차례다 - 횟수마다 따로 찾아도 방은 한 번만 나온다
        Long 둘다차례 = startedSplit(여덟시간전, 첫째, 둘째);
        setRequestCount(둘다차례, 첫째, 2);
        // 보내는 사람이 모두 보냈거나 확인됐으면 받는 사람의 줄만 남아도 대상이 아니다
        Long 보냄 = startedSplit(여덟시간전);
        jdbc.update("UPDATE fare_split_transfer SET sent_at = ? WHERE match_id = ? AND user_id <> ?", Timestamp.from(NOON.minus(Duration.ofHours(1))), 보냄, 받는사람);
        Long 현금으로확인됨 = startedSplit(여덟시간전, 첫째, 둘째);
        jdbc.update("UPDATE fare_split_transfer SET confirmed_at = ? WHERE match_id = ? AND user_id <> ?",
                Timestamp.from(NOON.minus(Duration.ofHours(1))), 현금으로확인됨, 받는사람);
        Long 나중방 = startedSplit(여덟시간전);

        List<Long> targets = fareSplits.findTransferRequestTargets(NOON);

        assertThat(targets).doesNotContain(보냄, 현금으로확인됨).doesNotHaveDuplicates().isSorted();
        assertThat(mine(targets)).containsExactly(둘다차례, 나중방);
    }

    @Test
    void 받는_사람의_줄은_요청_횟수가_차례와_맞아도_재요청_대상을_만들지_않는다() {
        // 받는 사람의 줄은 CONFIRMED 다 - 요청 횟수를 차례가 된 값(1)으로 억지로 맞춰도 보내는 사람이 모두 보냈으면 대상이 아니다
        Long room = startedSplit(NOON.minus(Duration.ofHours(4)));
        service.markSent(room, 첫째);
        setRequestCount(room, 받는사람, 1);

        assertThat(fareSplits.findTransferRequestTargets(NOON)).doesNotContain(room);

        notificationService.remindTransfers(room, NOON);

        assertThat(requestCount(room, 받는사람)).isEqualTo(1);
        assertThat(requestCount(room, 첫째)).isEqualTo(1);
        verify(notifier, never()).remindTransfer(any(), any());
    }

    @Test
    void 만료_대상은_경계_시각을_포함한다() {
        Long 만료 = startedSplit(NOON.minus(Duration.ofDays(1)));
        Long 만료전 = startedSplit(NOON.minus(Duration.ofDays(1)).plusSeconds(1));

        assertThat(fareSplits.findExpireTargets(NOON)).contains(만료).doesNotContain(만료전);
    }

    // ───────────────────────── 재요청 ─────────────────────────

    @Test
    void 재요청은_안_보낸_사람의_요청_횟수만_올리고_다음_차례_전에는_다시_올리지_않는다() {
        Long room = finishedRoom(받는사람, 첫째, 둘째);
        service.create(room, 받는사람, request(18_700, Bank.KAKAO, pay(받는사람, 6_700), pay(첫째, 7_000), pay(둘째, 5_000)));
        service.markSent(room, 둘째);
        startedAt(room, NOON.minus(Duration.ofHours(4)));

        // 요청을 2번 받은 뒤의 다음 차례는 시작 8시간 뒤다
        Instant next = NOON.plus(FareSplit.REMIND_INTERVAL);
        Instant beforeNext = next.minusSeconds(1);

        notificationService.remindTransfers(room, NOON);
        notificationService.remindTransfers(room, beforeNext);

        assertThat(requestCount(room, 첫째)).as("다음 차례 전에 다시 불러도 다시 보내지 않는다").isEqualTo(2);
        assertThat(requestCount(room, 둘째)).as("보냈다고 누른 사람은 재요청하지 않는다").isEqualTo(1);
        assertThat(requestCount(room, 받는사람)).as("받는 사람에게는 요청하지 않는다").isZero();
        assertThat(fareSplits.findTransferRequestTargets(beforeNext)).doesNotContain(room);
        verify(notifier).remindTransfer(room, List.of(new FarePayment(첫째, 7_000)));

        assertThat(fareSplits.findTransferRequestTargets(next)).contains(room);
        notificationService.remindTransfers(room, next);

        assertThat(requestCount(room, 첫째)).isEqualTo(3);
        assertThat(requestCount(room, 둘째)).isEqualTo(1);
        assertThat(requestCount(room, 받는사람)).isZero();
    }

    @Test
    void 재요청은_안_보낸_사람마다_자기가_보낼_금액으로_나가고_받는_사람에게는_나가지_않는다() {
        Long room = finishedRoom(받는사람, 첫째, 둘째);
        service.create(room, 받는사람, request(18_700, Bank.KAKAO, pay(받는사람, 6_700), pay(첫째, 7_000), pay(둘째, 5_000)));
        startedAt(room, NOON.minus(Duration.ofHours(4)));

        notificationService.remindTransfers(room, NOON);

        assertThat(requestCount(room, 첫째)).isEqualTo(2);
        assertThat(requestCount(room, 둘째)).isEqualTo(2);
        assertThat(requestCount(room, 받는사람)).as("받는 사람의 줄은 요청 횟수가 0 그대로다").isZero();
        ArgumentCaptor<List<FarePayment>> 재요청 = ArgumentCaptor.captor();
        verify(notifier).remindTransfer(eq(room), 재요청.capture());
        assertThat(재요청.getValue()).containsExactlyInAnyOrder(new FarePayment(첫째, 7_000), new FarePayment(둘째, 5_000));
    }

    @Test
    void 재요청_차례가_여러_번_밀렸으면_한_번만_보내고_밀린_차례는_버린다() {
        Long room = startedSplit(NOON.minus(Duration.ofHours(12)));

        notificationService.remindTransfers(room, NOON);

        assertThat(requestCount(room, 첫째)).as("4·8·12시간 차례를 한 번에 지난 것으로 친다").isEqualTo(4);
        verify(notifier).remindTransfer(room, List.of(new FarePayment(첫째, 5_000)));
        assertThat(fareSplits.findTransferRequestTargets(NOON)).as("밀린 차례를 다음 바퀴에 이어서 보내지 않는다").doesNotContain(room);
        assertThat(fareSplits.findTransferRequestTargets(NOON.plus(Duration.ofHours(4)))).as("다음 요청은 시작 16시간 차례").contains(room);
    }

    @Test
    void 요청을_6번_받은_사람에게는_다시_요청하지_않는다() {
        Long room = startedSplit(NOON.minus(Duration.ofHours(23)));
        setRequestCount(room, FareSplit.MAX_TRANSFER_REQUESTS);

        notificationService.remindTransfers(room, NOON);

        assertThat(requestCount(room, 첫째)).isEqualTo(FareSplit.MAX_TRANSFER_REQUESTS);
        assertThat(requestCount(room, 받는사람)).isZero();
        verify(notifier, never()).remindTransfer(any(), any());
    }

    // ───────────────────────── 스케줄러 한 바퀴 ─────────────────────────

    @Test
    void 스케줄러는_정산마다_따로_커밋해_만료하고_재요청한다() {
        Long 만료방 = startedSplit(NOON.minus(Duration.ofDays(3)));
        Long 재요청방 = startedSplit(NOON.minus(Duration.ofHours(4)));
        Long 차례전방 = startedSplit(NOON.minus(Duration.ofHours(4)).plusSeconds(1));
        // 다른 테스트가 남긴 정산을 건드리지 않게 실제 쿼리 결과 중 이 테스트의 방만 넘긴다. 처리는 실제(프록시) 서비스가 한다
        FareSplitSweeper sweeper = new FareSplitSweeper(onlyMyRooms(), service, notificationService);

        sweeper.sweepAt(NOON);

        assertThat(jdbc.queryForObject("SELECT status FROM fare_split WHERE match_id = ?", String.class, 만료방)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT closed_at FROM fare_split WHERE match_id = ?", Timestamp.class, 만료방).toInstant()).isEqualTo(NOON);
        assertThat(jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, 만료방)).isNull();
        assertThat(requestCount(만료방, 첫째)).as("만료를 먼저 해 닫힌 정산은 재요청하지 않는다").isEqualTo(1);
        assertThat(requestCount(재요청방, 첫째)).isEqualTo(2);
        assertThat(requestCount(차례전방, 첫째)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM fare_split WHERE match_id = ?", String.class, 재요청방)).isEqualTo("OPEN");
        verify(notifier).remindTransfer(재요청방, List.of(new FarePayment(첫째, 5_000)));
        verify(notifier, never()).remindTransfer(eq(만료방), any());
        verify(notifier, never()).remindTransfer(eq(차례전방), any());
    }

    // ───────────────────────── 받는 사람 탈퇴 ─────────────────────────

    @Test
    void 받는_사람이_탈퇴하면_그_사람의_OPEN_정산만_계좌번호_컬럼이_NULL_이_되고_정산은_열려_있다() {
        Long 내정산 = finishedRoom(받는사람, 첫째);
        service.create(내정산, 받는사람, request(10_000, Bank.KB, pay(받는사람, 5_000), pay(첫째, 5_000)));
        Long 남의정산 = finishedRoom(첫째, 받는사람);
        service.create(남의정산, 첫째, request(10_000, Bank.KB, pay(첫째, 5_000), pay(받는사람, 5_000)));

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
        service.create(room, 받는사람, request(10_000, Bank.KB, pay(받는사람, 5_000), pay(첫째, 5_000)));
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
        assertThat(requestCount(room, 첫째)).isEqualTo(2);
    }

    @Test
    void 재요청과_보냈어요가_동시에_와도_보낸_시각을_잃지_않는다() throws Exception {
        // 잠금이 없으면 재요청이 앞서 읽은 송금 행 전체를 덮어써 보낸 시각을 잃는다 - 여러 방에서 되풀이해 경합을 만든다
        for(int i = 0; i < 3; i++) {
            Long room = finishedRoom(받는사람, 첫째);
            service.create(room, 받는사람, request(10_000, Bank.KB, pay(받는사람, 5_000), pay(첫째, 5_000)));
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

    /** 받는 사람(방장)·첫째가 탄 방에 startedAt 에 시작한 정산 */
    private Long startedSplit(Instant startedAt) {
        return startedSplit(startedAt, 첫째);
    }

    /** 받는 사람(방장)과 senders 가 탄 방에 startedAt 에 시작한 정산. 택시비는 2만 원이고 보내는 사람마다 5천 원, 받는 사람이 나머지를 부담한다 */
    private Long startedSplit(Instant startedAt, Long... senders) {
        List<Long> members = new ArrayList<>(List.of(받는사람));
        members.addAll(List.of(senders));
        Long room = finishedRoom(members.toArray(Long[]::new));
        FareSplitRequest.Payment[] payments = Stream.concat(Stream.of(pay(받는사람, 20_000 - 5_000 * senders.length)),
                Stream.of(senders).map(sender -> pay(sender, 5_000))).toArray(FareSplitRequest.Payment[]::new);
        service.create(room, 받는사람, request(20_000, Bank.KB, payments));
        startedAt(room, startedAt);

        return room;
    }

    /** 받는 사람(방장)을 포함한 탑승자마다 부담 금액을 실은 정산 시작 요청 */
    private static FareSplitRequest request(int totalFare, Bank bank, FareSplitRequest.Payment... payments) {
        return new FareSplitRequest(totalFare, bank, RAW_ACCOUNT, List.of(payments));
    }

    /** userId 가 amount 를 부담한다 - 요청은 사람을 publicId 로 가리킨다 */
    private FareSplitRequest.Payment pay(Long userId, int amount) {
        UUID publicId = jdbc.queryForObject("SELECT public_id FROM users WHERE id = ?", UUID.class, userId);

        return new FareSplitRequest.Payment(publicId, amount);
    }

    /** 정산 시작 요청이 publicId 로 가리킬 실제 users 행 */
    private Long newUser() {
        Long id = jdbc.queryForObject("INSERT INTO users (public_id, login_type, created_at) VALUES (?, 'LOCAL', now()) RETURNING id",
                Long.class, UUID.randomUUID());
        users.add(id);

        return id;
    }

    /** 넘긴 순서대로 합류하고 모두 도착해 출발한(FINISHED) 방. 방장은 맨 앞 사람이다 */
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

        return targets;
    }

    private List<Long> mine(List<Long> partyIds) {
        return partyIds.stream().filter(rooms::contains).toList();
    }

    /** 정산 시작 시각을 startedAt 으로 맞춘다 - 첫 송금 요청(request_count 1)은 정산을 시작하며 이미 보냈다 */
    private void startedAt(Long room, Instant startedAt) {
        jdbc.update("UPDATE fare_split SET created_at = ? WHERE match_id = ?", Timestamp.from(startedAt), room);
    }

    /** 방의 보내는 사람 모두가 송금 요청을 requestCount 번 받은 것으로 맞춘다 - 받는 사람의 줄(요청 0번)은 그대로 둔다 */
    private void setRequestCount(Long room, int requestCount) {
        jdbc.update("""
                UPDATE fare_split_transfer t SET request_count = ?
                WHERE t.match_id = ? AND t.user_id <> (SELECT s.payer_id FROM fare_split s WHERE s.match_id = t.match_id)
                """, requestCount, room);
    }

    private void setRequestCount(Long room, Long userId, int requestCount) {
        jdbc.update("UPDATE fare_split_transfer SET request_count = ? WHERE match_id = ? AND user_id = ?", requestCount, room, userId);
    }

    private int requestCount(Long room, Long userId) {
        return jdbc.queryForObject("SELECT request_count FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", Integer.class, room, userId);
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
