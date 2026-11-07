package team.codingforest.moyeota.matching.faresplit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.matching.faresplit.domain.Bank;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplit;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitRole;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplits;
import team.codingforest.moyeota.matching.faresplit.domain.TossDeeplink;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 *  V35 의 fare_split·fare_split_transfer 제약, 계좌번호 저장 암호화, 송금 합류 순 정렬,
 *  정산 시작이 방 행 잠금으로 줄 서서 방마다 하나만 만들어지는지를 실제 Postgres 에서 본다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class FareSplitPostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 광주 좌표를 쓴다
    private static final Location DEPARTURE = new Location(35.1595, 126.8526);
    private static final Location DESTINATION = new Location(35.1468, 126.9220);
    private static final String RAW_ACCOUNT = "3333-01-2345678";
    private static final String ACCOUNT_DIGITS = "3333012345678";

    @Autowired FareSplitService service;
    @Autowired FareSplits fareSplits;
    @Autowired Parties parties;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    // 다른 테스트·이전 실행과 섞이지 않게 매번 새 번호를 쓴다(users 행은 필요 없다 - 정산은 users 에 FK 를 걸지 않는다)
    private final Long 방장 = ThreadLocalRandom.current().nextLong(7_000_000_000L, 8_000_000_000L);
    private final Long 둘째 = 방장 + 1;
    private final Long 셋째 = 방장 + 2;
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

    // ───────────────────────── V35 제약 ─────────────────────────

    @Test
    void 상태는_OPEN_COMPLETED_EXPIRED_만_받는다() {
        Long room = finishedRoom(방장, 둘째);

        assertThatThrownBy(() -> insertSplit(room, "CANCELED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertSplit(room, "EXPIRED");
    }

    @Test
    void 없는_방에는_정산을_만들_수_없다() {
        assertThatThrownBy(() -> insertSplit(-1L, "OPEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 송금_행은_있는_정산에만_붙고_한_정산에_한_사람은_한_행이며_요청_횟수는_0으로_시작한다() {
        Long room = finishedRoom(방장, 둘째);

        assertThatThrownBy(() -> insertTransfer(room, 둘째))
                .as("정산이 없으면 FK 위반").isInstanceOf(DataIntegrityViolationException.class);

        insertSplit(room, "OPEN");
        insertTransfer(room, 둘째);

        assertThatThrownBy(() -> insertTransfer(room, 둘째))
                .as("(match_id, user_id) PK").isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT request_count FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", Integer.class, room, 둘째))
                .isZero();
    }

    @Test
    void 같은_방에_정산을_두_번_INSERT_하면_덮어쓰지_않고_PK_위반이다() {
        Long room = finishedRoom(방장, 둘째);
        Party party = parties.findById(room).orElseThrow();
        tx.executeWithoutResult(status -> fareSplits.create(FareSplit.open(party, 방장, 10_000, Bank.KB, AccountNumber.of(RAW_ACCOUNT), Instant.now())));

        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                fareSplits.create(FareSplit.open(party, 둘째, 20_000, Bank.KB, AccountNumber.of(RAW_ACCOUNT), Instant.now()))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT payer_id FROM fare_split WHERE match_id = ?", Long.class, room)).isEqualTo(방장);
    }

    // ───────────────────────── 계좌번호 암호화 ─────────────────────────

    @Test
    void 계좌번호는_암호문으로_저장되고_읽으면_숫자만_남은_평문으로_돌아온다() {
        Long room = finishedRoom(방장, 둘째, 셋째);

        service.create(room, 방장, 18_700, Bank.KAKAO, RAW_ACCOUNT);

        String stored = jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, room);
        assertThat(stored).startsWith("v1:").isNotEqualTo(ACCOUNT_DIGITS).doesNotContain(ACCOUNT_DIGITS);
        String read = tx.execute(status -> fareSplits.findByPartyId(room).orElseThrow().getAccountNumber().value());
        assertThat(read).isEqualTo(ACCOUNT_DIGITS);

        FareSplitDetailResponse sender = service.getDetail(room, 셋째);
        assertThat(sender.accountNumber()).isEqualTo(ACCOUNT_DIGITS);
        assertThat(sender.tossDeeplink()).isEqualTo(TossDeeplink.of(6_240, Bank.KAKAO, new AccountNumber(ACCOUNT_DIGITS)));
    }

    // ───────────────────────── 시작·조회 ─────────────────────────

    @Test
    void 정산을_시작하면_보내는_사람마다_송금_행이_생기고_조회는_방_합류_순으로_준다() {
        // userId 순(방장 < 둘째 < 셋째)과 다르게 셋째가 둘째보다 먼저 들어왔다
        Long room = finishedRoom(방장, 셋째, 둘째);

        FareSplitDetailResponse created = service.create(room, 방장, 18_700, Bank.KAKAO, RAW_ACCOUNT);

        assertThat(created.myRole()).isEqualTo(FareSplitRole.PAYER);
        assertThat(created.status()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(created.amountPerPerson()).isEqualTo(6_240);
        assertThat(created.myAmount()).isEqualTo(6_220);
        assertThat(jdbc.queryForList("SELECT user_id FROM fare_split_transfer WHERE match_id = ? ORDER BY user_id", Long.class, room))
                .containsExactly(둘째, 셋째);

        FareSplit found = tx.execute(status -> fareSplits.findByPartyId(room).orElseThrow());
        assertThat(found.getTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(셋째, 둘째);

        assertThatThrownBy(() -> service.getDetail(room, 방장 + 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(MatchingErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    // ───────────────────────── 동시성 ─────────────────────────

    @Test
    void 정산_시작은_방_행_잠금을_기다렸다가_돈다() throws Exception {
        Long room = finishedRoom(방장, 둘째);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
            parties.findByIdForUpdate(room).orElseThrow();
            locked.countDown();
            awaitQuietly(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        Future<FareSplitDetailResponse> creator = pool.submit(() -> service.create(room, 방장, 10_000, Bank.KB, RAW_ACCOUNT));
        await().atMost(Duration.ofSeconds(5))
                .until(() -> jdbc.queryForObject("""
                        select count(*) from pg_stat_activity
                        where datname = current_database() and wait_event_type = 'Lock' and query like '%match_room%for no key update%'
                        """, Integer.class) > 0);
        assertThat(creator.isDone()).as("방 잠금이 풀리기 전에는 만들지 못한다").isFalse();

        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        assertThat(creator.get(10, TimeUnit.SECONDS).partyId()).isEqualTo(room);
    }

    @Test
    void 탑승자들이_동시에_정산을_시작해도_하나만_만들어지고_나머지는_FARE_SPLIT_ALREADY_EXISTS() throws Exception {
        Long room = finishedRoom(방장, 둘째, 셋째);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<FareSplitDetailResponse>> futures = new ArrayList<>();

        for(Long payer : List.of(방장, 둘째, 셋째, 방장)) {
            Callable<FareSplitDetailResponse> create = () -> {
                start.await();
                return service.create(room, payer, 12_000, Bank.TOSS, RAW_ACCOUNT);
            };
            futures.add(pool.submit(create));
        }
        start.countDown();

        int succeeded = 0;
        int conflicted = 0;
        for(Future<FareSplitDetailResponse> future : futures) {
            try {
                future.get(20, TimeUnit.SECONDS);
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) e.getCause()).getErrorCode()).isEqualTo(MatchingErrorCode.FARE_SPLIT_ALREADY_EXISTS);
                conflicted++;
            }
        }

        assertThat(succeeded).isEqualTo(1);
        assertThat(conflicted).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split WHERE match_id = ?", Integer.class, room)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split_transfer WHERE match_id = ?", Integer.class, room)).isEqualTo(2);
    }

    // ───────────────────────── fixtures ─────────────────────────

    /** 넘긴 순서대로 합류하고 모두 도착해 출발한(FINISHED) 방. 합류 시각(created_at)도 그 순서로 박는다 */
    private Long finishedRoom(Long... memberIdsInJoinOrder) {
        Instant meetingAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofHours(1));
        List<PartyMember> members = new ArrayList<>();
        for(Long memberId : memberIdsInJoinOrder) {
            members.add(PartyMember.load(memberId, meetingAt, meetingAt, null, null));
        }

        Long id = parties.save(Party.load(null, DEPARTURE, DESTINATION, "광주송정역", "전남대", new Capacity(3), members,
                memberIdsInJoinOrder[0], meetingAt.minus(Duration.ofHours(1)), PartyStatus.FINISHED,
                12000, 25, "encoded-path", 0L, RoomType.REALTIME, meetingAt, null)).getId();
        rooms.add(id);

        for(int i = 0; i < memberIdsInJoinOrder.length; i++) {
            jdbc.update("UPDATE user_match_room SET created_at = ? WHERE match_id = ? AND user_id = ?",
                    java.sql.Timestamp.from(meetingAt.minusSeconds(60 - i * 10L)), id, memberIdsInJoinOrder[i]);
        }

        return id;
    }

    private void insertSplit(Long room, String status) {
        jdbc.update("""
                INSERT INTO fare_split (match_id, payer_id, total_fare, amount_per_person, bank_code, account_number, status, created_at)
                VALUES (?, ?, 10000, 5000, 'KB', NULL, ?, now())
                """, room, 방장, status);
    }

    private void insertTransfer(Long room, Long userId) {
        jdbc.update("INSERT INTO fare_split_transfer (match_id, user_id, created_at) VALUES (?, ?, now())", room, userId);
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
