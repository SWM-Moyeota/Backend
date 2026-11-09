package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitRole;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.faresplit.domain.RideRoster;
import team.codingforest.moyeota.faresplit.domain.TossDeeplink;
import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;
import team.codingforest.moyeota.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.faresplit.dto.MyFareSplitListResponse;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;
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
import java.util.UUID;
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
 *  V35·V36 의 fare_split·fare_split_transfer 제약, 계좌번호 저장 암호화, 응답의 송금 합류 순 정렬(저장소는 userId 순),
 *  정산 시작이 방 행을 잠그지 않고도 PK 로 방마다 하나만 만들어지는지, 송금 처리·수정·취소가 정산 행 잠금으로 줄 서는지,
 *  닫히면 계좌번호 컬럼이 NULL 이 되는지, 내가 보낼 정산 목록 쿼리를 실제 Postgres 에서 본다
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
    private final List<Long> users = new ArrayList<>();
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
        users.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
    }

    // ───────────────────────── V35·V36 제약 ─────────────────────────

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
    void 송금_행은_있는_정산에만_붙고_한_정산에_한_사람은_한_행이며_마지막_송금_요청_시각을_비울_수_없다() {
        Long room = finishedRoom(방장, 둘째);

        assertThatThrownBy(() -> insertTransfer(room, 둘째))
                .as("정산이 없으면 FK 위반").isInstanceOf(DataIntegrityViolationException.class);

        insertSplit(room, "OPEN");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO fare_split_transfer (match_id, user_id, created_at) VALUES (?, ?, now())", room, 둘째))
                .as("첫 송금 요청부터 늘 채운다(NOT NULL)").isInstanceOf(DataIntegrityViolationException.class);
        insertTransfer(room, 둘째);

        assertThatThrownBy(() -> insertTransfer(room, 둘째))
                .as("(match_id, user_id) PK").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_방에_정산을_두_번_INSERT_하면_덮어쓰지_않고_PK_위반을_중복_키로_알린다() {
        Long room = finishedRoom(방장, 둘째);
        RideRoster party = new RideRoster(room, true, List.of(방장, 둘째), List.of(방장, 둘째));
        tx.executeWithoutResult(status -> fareSplits.create(FareSplit.create(party, 방장, 10_000, Bank.KB, AccountNumber.of(RAW_ACCOUNT), Instant.now())));

        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                fareSplits.create(FareSplit.create(party, 둘째, 20_000, Bank.KB, AccountNumber.of(RAW_ACCOUNT), Instant.now()))))
                .isInstanceOf(DuplicateKeyException.class);
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
    void 정산을_시작하면_보내는_사람마다_송금_행이_생기고_시작_상세_수정_응답은_송금을_방_합류_순으로_준다() {
        // userId 순(받는사람 < 앞번호 < 뒷번호)과 다르게 뒷번호가 앞번호보다 먼저 들어왔다
        Long 받는사람 = newUser();
        Long 앞번호 = newUser();
        Long 뒷번호 = newUser();
        Long room = finishedRoom(받는사람, 뒷번호, 앞번호);
        List<UUID> 합류순 = List.of(publicIdOf(뒷번호), publicIdOf(앞번호));

        FareSplitDetailResponse created = service.create(room, 받는사람, 18_700, Bank.KAKAO, RAW_ACCOUNT);

        assertThat(created.myRole()).isEqualTo(FareSplitRole.PAYER);
        assertThat(created.status()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(created.amountPerPerson()).isEqualTo(6_240);
        assertThat(created.myAmount()).isEqualTo(6_220);
        assertThat(created.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId).containsExactlyElementsOf(합류순);
        assertThat(jdbc.queryForList("SELECT user_id FROM fare_split_transfer WHERE match_id = ? ORDER BY user_id", Long.class, room))
                .containsExactly(앞번호, 뒷번호);

        FareSplit found = tx.execute(status -> fareSplits.findByPartyId(room).orElseThrow());
        assertThat(found.getTransfers()).extracting(FareSplitTransfer::getMemberId).as("저장소는 방을 읽지 않고 userId 순으로 준다")
                .containsExactly(앞번호, 뒷번호);

        assertThat(service.getDetail(room, 앞번호).transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId)
                .containsExactlyElementsOf(합류순);
        assertThat(service.edit(room, 받는사람, 20_000, Bank.SHINHAN, "110-123-456789").transfers())
                .extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId).containsExactlyElementsOf(합류순);

        // 필드의 방장 번호는 이 방(실제 users 로 만들었다)의 멤버가 아니다
        assertThatThrownBy(() -> service.getDetail(room, 방장))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    // ───────────────────────── 동시성 ─────────────────────────

    @Test
    void 정산_시작은_방_행을_잠그지_않아_방_행_잠금을_기다리지_않는다() throws Exception {
        Long room = finishedRoom(방장, 둘째);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // 같은 방의 다른 흐름(노쇼 무효 처리 등)이 방 행을 잠그고 있다
        Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
            parties.findByIdForUpdate(room).orElseThrow();
            locked.countDown();
            awaitQuietly(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        try {
            FareSplitDetailResponse created = pool.submit(() -> service.create(room, 방장, 10_000, Bank.KB, RAW_ACCOUNT)).get(10, TimeUnit.SECONDS);

            assertThat(created.partyId()).as("방 잠금이 풀리기 전에 만들어진다").isEqualTo(room);
            assertThat(holder.isDone()).as("방 잠금은 아직 잡혀 있다").isFalse();
        } finally {
            release.countDown();
        }
        holder.get(10, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT payer_id FROM fare_split WHERE match_id = ?", Long.class, room)).isEqualTo(방장);
    }

    @Test
    void 탑승자들이_동시에_정산을_시작해도_PK_로_하나만_만들어지고_나머지는_FARE_SPLIT_ALREADY_EXISTS() throws Exception {
        List<Long> riders = List.of(방장, 둘째, 셋째);

        // 방 잠금 없이 PK 만으로 막으므로 선검사를 함께 통과한 INSERT 끼리 부딪히게 여러 방에서 되풀이한다
        for(int i = 0; i < 3; i++) {
            Long room = finishedRoom(방장, 둘째, 셋째);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Long>> futures = new ArrayList<>();

            for(Long payer : List.of(방장, 둘째, 셋째, 방장)) {
                Callable<Long> create = () -> {
                    start.await();
                    service.create(room, payer, 12_000, Bank.TOSS, RAW_ACCOUNT);
                    return payer;
                };
                futures.add(pool.submit(create));
            }
            start.countDown();

            List<Long> winners = new ArrayList<>();
            int conflicted = 0;
            for(Future<Long> future : futures) {
                try {
                    winners.add(future.get(20, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) e.getCause()).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_ALREADY_EXISTS);
                    conflicted++;
                }
            }

            assertThat(winners).hasSize(1);
            assertThat(conflicted).isEqualTo(3);
            Long winner = winners.getFirst();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split WHERE match_id = ?", Integer.class, room)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT payer_id FROM fare_split WHERE match_id = ?", Long.class, room)).isEqualTo(winner);
            assertThat(jdbc.queryForList("SELECT user_id FROM fare_split_transfer WHERE match_id = ?", Long.class, room))
                    .as("이긴 정산의 보내는 사람만 남는다").containsExactlyInAnyOrderElementsOf(riders.stream().filter(id -> !id.equals(winner)).toList());
        }
    }


    // ───────────────────────── 송금 처리·완료 ─────────────────────────

    @Test
    void 보냈어요와_못_받았어요를_오가다_모두_확인되면_COMPLETED_로_닫히고_계좌번호_컬럼이_NULL_이_된다() {
        Long 받는사람 = newUser();
        Long 보낸사람 = newUser();
        Long 현금낸사람 = newUser();
        Long room = finishedRoom(받는사람, 보낸사람, 현금낸사람);
        service.create(room, 받는사람, 18_700, Bank.KAKAO, RAW_ACCOUNT);

        service.markSent(room, 보낸사람);
        service.markSent(room, 보낸사람);
        assertThat(transferStatus(room, 보낸사람)).isEqualTo(TransferStatus.SENT);

        java.sql.Timestamp firstRequestedAt = jdbc.queryForObject("SELECT last_requested_at FROM fare_split_transfer WHERE match_id = ? AND user_id = ?",
                java.sql.Timestamp.class, room, 보낸사람);
        service.markNotReceived(room, 받는사람, publicIdOf(보낸사람));
        assertThat(jdbc.queryForObject("SELECT sent_at FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", java.sql.Timestamp.class, room, 보낸사람))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT last_requested_at FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", java.sql.Timestamp.class, room, 보낸사람)
                .toInstant()).as("못 받았어요 알림을 송금 요청으로 친다").isAfter(firstRequestedAt.toInstant());
        assertThatThrownBy(() -> service.markNotReceived(room, 받는사람, publicIdOf(보낸사람)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.TRANSFER_NOT_SENT);

        service.markSent(room, 보낸사람);
        service.confirmReceived(room, 받는사람, publicIdOf(보낸사람));
        assertThat(jdbc.queryForObject("SELECT status FROM fare_split WHERE match_id = ?", String.class, room)).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, room)).isNotNull();

        service.confirmReceived(room, 받는사람, publicIdOf(현금낸사람));
        service.confirmReceived(room, 받는사람, publicIdOf(현금낸사람));

        assertThat(jdbc.queryForObject("SELECT status FROM fare_split WHERE match_id = ?", String.class, room)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT closed_at FROM fare_split WHERE match_id = ?", java.sql.Timestamp.class, room)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, room)).isNull();
        assertThat(jdbc.queryForObject("SELECT sent_at FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", java.sql.Timestamp.class, room, 현금낸사람))
                .as("현금 등으로 바로 확인").isNull();

        FareSplitDetailResponse detail = service.getDetail(room, 보낸사람);
        assertThat(detail.status()).isEqualTo(FareSplitStatus.COMPLETED);
        assertThat(detail.accountNumber()).isNull();
        assertThat(detail.tossDeeplink()).isNull();
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::status).containsOnly(TransferStatus.CONFIRMED);
    }

    // ───────────────────────── 수정·취소 ─────────────────────────

    @Test
    void 아무도_보내기_전이면_수정이_1인당_금액을_다시_계산하고_새_계좌를_암호문으로_저장한다() {
        Long room = finishedRoom(방장, 둘째, 셋째);
        service.create(room, 방장, 18_700, Bank.KAKAO, RAW_ACCOUNT);
        String before = jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, room);

        FareSplitDetailResponse edited = service.edit(room, 방장, 20_000, Bank.SHINHAN, "110-123-456789");

        assertThat(edited.amountPerPerson()).isEqualTo(6_670);
        assertThat(edited.myAmount()).isEqualTo(6_660);
        assertThat(jdbc.queryForMap("SELECT total_fare, amount_per_person, bank_code FROM fare_split WHERE match_id = ?", room))
                .containsEntry("total_fare", 20_000).containsEntry("amount_per_person", 6_670).containsEntry("bank_code", "SHINHAN");
        String after = jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, room);
        assertThat(after).startsWith("v1:").isNotEqualTo(before).doesNotContain("110123456789");
        assertThat(service.getDetail(room, 둘째).accountNumber()).isEqualTo("110123456789");
    }

    @Test
    void 취소하면_정산과_송금_행이_모두_지워지고_같은_방에서_다시_시작할_수_있다() {
        Long room = finishedRoom(방장, 둘째, 셋째);
        service.create(room, 방장, 18_700, Bank.KAKAO, RAW_ACCOUNT);

        service.cancel(room, 방장);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split WHERE match_id = ?", Integer.class, room)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split_transfer WHERE match_id = ?", Integer.class, room)).isZero();
        assertThatThrownBy(() -> service.getDetail(room, 방장))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_NOT_FOUND);

        assertThat(service.create(room, 둘째, 12_000, Bank.TOSS, RAW_ACCOUNT).myRole()).isEqualTo(FareSplitRole.PAYER);
    }

    @Test
    void 누군가_보냈다고_누른_뒤에는_수정도_취소도_FARE_SPLIT_ALREADY_STARTED_이고_그대로_남는다() {
        Long room = finishedRoom(방장, 둘째, 셋째);
        service.create(room, 방장, 18_700, Bank.KAKAO, RAW_ACCOUNT);
        service.markSent(room, 셋째);

        assertThatThrownBy(() -> service.edit(room, 방장, 20_000, Bank.SHINHAN, "110-123-456789"))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_ALREADY_STARTED);
        assertThatThrownBy(() -> service.cancel(room, 방장))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_ALREADY_STARTED);

        assertThat(jdbc.queryForObject("SELECT total_fare FROM fare_split WHERE match_id = ?", Integer.class, room)).isEqualTo(18_700);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split_transfer WHERE match_id = ?", Integer.class, room)).isEqualTo(2);
    }

    // ───────────────────────── 내가 보낼 정산 ─────────────────────────

    @Test
    void 내가_보낼_정산은_내가_보내는_사람이고_아직_안_보낸_OPEN_정산만_최신순으로_준다() {
        Long 나 = newUser();
        Long 받는사람 = newUser();
        Long 보낼방 = finishedRoom(받는사람, 나);
        Long 보낸방 = finishedRoom(받는사람, 나);
        Long 끝난방 = finishedRoom(받는사람, 나);
        Long 내가받는방 = finishedRoom(나, 받는사람);
        Long 최근방 = finishedRoom(받는사람, 나);
        service.create(보낼방, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
        service.create(보낸방, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
        service.create(끝난방, 받는사람, 10_000, Bank.KB, RAW_ACCOUNT);
        service.create(내가받는방, 나, 10_000, Bank.KB, RAW_ACCOUNT);
        service.create(최근방, 받는사람, 12_000, Bank.KB, RAW_ACCOUNT);
        jdbc.update("UPDATE fare_split SET created_at = created_at - interval '1 hour' WHERE match_id = ?", 보낼방);
        service.markSent(보낸방, 나);
        service.confirmReceived(끝난방, 받는사람, publicIdOf(나));

        MyFareSplitListResponse response = service.findMine(나, TransferStatus.NOT_SENT);

        assertThat(response.fareSplits()).extracting(MyFareSplitListResponse.MyFareSplitItem::partyId).containsExactly(최근방, 보낼방);
        MyFareSplitListResponse.MyFareSplitItem first = response.fareSplits().get(0);
        assertThat(first.departure()).isEqualTo("광주송정역");
        assertThat(first.destination()).isEqualTo("전남대");
        assertThat(first.payer().publicId()).isEqualTo(publicIdOf(받는사람));
        assertThat(first.myAmount()).isEqualTo(6_000);
        assertThat(service.findMine(나, TransferStatus.SENT).fareSplits())
                .extracting(MyFareSplitListResponse.MyFareSplitItem::partyId).containsExactly(보낸방);
    }

    // ───────────────────────── 정산 행 잠금 ─────────────────────────

    @Test
    void 송금_처리는_정산_행_잠금을_기다렸다가_돈다() throws Exception {
        Long room = finishedRoom(방장, 둘째);
        service.create(room, 방장, 10_000, Bank.KB, RAW_ACCOUNT);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
            fareSplits.findByPartyIdForUpdate(room).orElseThrow();
            locked.countDown();
            awaitQuietly(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        Future<?> sender = pool.submit(() -> service.markSent(room, 둘째));
        await().atMost(Duration.ofSeconds(5))
                .until(() -> jdbc.queryForObject("""
                        select count(*) from pg_stat_activity
                        where datname = current_database() and wait_event_type = 'Lock' and query like '%fare_split%for no key update%'
                        """, Integer.class) > 0);
        assertThat(sender.isDone()).as("정산 잠금이 풀리기 전에는 바꾸지 못한다").isFalse();

        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        sender.get(10, TimeUnit.SECONDS);
        assertThat(transferStatus(room, 둘째)).isEqualTo(TransferStatus.SENT);
    }

    @Test
    void 마지막_두_송금을_동시에_확인해도_정산은_COMPLETED_로_닫히고_계좌번호가_지워진다() throws Exception {
        Long 받는사람 = newUser();
        Long 첫째 = newUser();
        Long 둘 = newUser();

        // 잠금이 없으면 두 확인이 서로의 확인을 못 보고 OPEN 으로 남는다 - 여러 방에서 되풀이해 경합을 만든다
        for(int i = 0; i < 3; i++) {
            Long room = finishedRoom(받는사람, 첫째, 둘);
            service.create(room, 받는사람, 18_700, Bank.KAKAO, RAW_ACCOUNT);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for(Long sender : List.of(첫째, 둘)) {
                UUID publicId = publicIdOf(sender);
                futures.add(pool.submit(() -> {
                    start.await();
                    service.confirmReceived(room, 받는사람, publicId);
                    return null;
                }));
            }
            start.countDown();
            for(Future<?> future : futures) future.get(20, TimeUnit.SECONDS);

            assertThat(jdbc.queryForObject("SELECT status FROM fare_split WHERE match_id = ?", String.class, room)).isEqualTo("COMPLETED");
            assertThat(jdbc.queryForObject("SELECT closed_at FROM fare_split WHERE match_id = ?", java.sql.Timestamp.class, room)).isNotNull();
            assertThat(jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, room)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split_transfer WHERE match_id = ? AND confirmed_at IS NOT NULL", Integer.class, room))
                    .isEqualTo(2);
        }
    }

    @Test
    void 보냈어요와_취소가_동시에_와도_둘_중_하나만_이기고_어긋난_상태가_남지_않는다() throws Exception {
        for(int i = 0; i < 3; i++) {
            Long room = finishedRoom(방장, 둘째);
            service.create(room, 방장, 10_000, Bank.KB, RAW_ACCOUNT);
            CountDownLatch start = new CountDownLatch(1);

            Future<?> sent = pool.submit(() -> {
                start.await();
                service.markSent(room, 둘째);
                return null;
            });
            Future<?> cancel = pool.submit(() -> {
                start.await();
                service.cancel(room, 방장);
                return null;
            });
            start.countDown();

            FareSplitErrorCode sentError = errorOf(sent);
            FareSplitErrorCode cancelError = errorOf(cancel);
            int splits = jdbc.queryForObject("SELECT count(*) FROM fare_split WHERE match_id = ?", Integer.class, room);

            if(cancelError == null) {
                // 취소가 먼저 잠갔다 - 정산이 지워져 보냈어요는 찾지 못한다
                assertThat(sentError).isEqualTo(FareSplitErrorCode.FARE_SPLIT_NOT_FOUND);
                assertThat(splits).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split_transfer WHERE match_id = ?", Integer.class, room)).isZero();
            }
            else {
                // 보냈어요가 먼저 잠갔다 - 송금이 시작돼 취소할 수 없다
                assertThat(cancelError).isEqualTo(FareSplitErrorCode.FARE_SPLIT_ALREADY_STARTED);
                assertThat(sentError).isNull();
                assertThat(splits).isEqualTo(1);
                assertThat(transferStatus(room, 둘째)).isEqualTo(TransferStatus.SENT);
            }
        }
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

    /** publicId 로 보내는 사람을 찾는 처리(받았어요·못 받았어요)와 응답 송금의 publicId(합류 순) 확인에 쓰는 실제 users 행 */
    private Long newUser() {
        Long id = jdbc.queryForObject("INSERT INTO users (public_id, login_type, created_at) VALUES (?, 'LOCAL', now()) RETURNING id",
                Long.class, UUID.randomUUID());
        users.add(id);

        return id;
    }

    private UUID publicIdOf(Long userId) {
        return jdbc.queryForObject("SELECT public_id FROM users WHERE id = ?", UUID.class, userId);
    }

    private TransferStatus transferStatus(Long room, Long userId) {
        return tx.execute(status -> fareSplits.findByPartyId(room).orElseThrow().findTransfer(userId).orElseThrow().status());
    }

    /** 성공했으면 null, BusinessException 이면 그 코드 */
    private static FareSplitErrorCode errorOf(Future<?> future) throws Exception {
        try {
            future.get(20, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(BusinessException.class);
            return (FareSplitErrorCode) ((BusinessException) e.getCause()).getErrorCode();
        }
    }

    private void insertSplit(Long room, String status) {
        jdbc.update("""
                INSERT INTO fare_split (match_id, payer_id, total_fare, amount_per_person, bank_code, account_number, status, created_at)
                VALUES (?, ?, 10000, 5000, 'KB', NULL, ?, now())
                """, room, 방장, status);
    }

    private void insertTransfer(Long room, Long userId) {
        jdbc.update("INSERT INTO fare_split_transfer (match_id, user_id, last_requested_at, created_at) VALUES (?, ?, now(), now())", room, userId);
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
