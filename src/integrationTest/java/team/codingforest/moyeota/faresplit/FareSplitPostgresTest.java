package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.FarePayment;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotifier;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitRole;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.faresplit.domain.RideRoster;
import team.codingforest.moyeota.faresplit.domain.TossDeeplink;
import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;
import team.codingforest.moyeota.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.faresplit.dto.FareSplitRequest;
import team.codingforest.moyeota.faresplit.dto.MyFareSplitListResponse;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 *  V35·V36·V39 의 fare_split·fare_split_transfer 제약, 계좌번호 저장 암호화, 응답의 송금 합류 순 정렬(저장소는 userId 순),
 *  받는 사람(방장)이 탑승자마다 넣은 부담 금액이 방장의 줄까지 장부에 그대로 남고(방장의 줄은 처음부터 CONFIRMED·요청 0번)
 *  탑승자·금액 합이 맞지 않으면 아무것도 남지 않는지, 응답 송금·알림·내가 보낼 정산에 방장이 끼지 않는지,
 *  정산 시작이 방 행을 잠그지 않고도 PK 로 방마다 하나만 만들어지는지, 송금 처리·수정이 정산 행 잠금으로 줄 서는지,
 *  보냈어요 알림이 커밋 뒤에만 나가는지, 닫히면 계좌번호 컬럼이 NULL 이 되는지, 내가 보낼 정산 목록 쿼리를 실제 Postgres 에서 본다
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
    // 실제 알림 구현을 그대로 두고 호출만 본다
    @MockitoSpyBean FareSplitNotifier notifier;

    // 정산 시작·수정은 방장을 포함한 탑승자를 publicId 로 받는다 - 다른 테스트·이전 실행과 섞이지 않게 매번 실제 users 행을 새로 만든다
    private Long 방장;
    private Long 둘째;
    private Long 셋째;
    private final List<Long> rooms = new ArrayList<>();
    private final List<Long> users = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @BeforeEach
    void setUp() {
        방장 = newUser();
        둘째 = newUser();
        셋째 = newUser();
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

    // ───────────────────────── V35·V36·V39 제약 ─────────────────────────

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
    void 송금_행은_있는_정산에만_붙고_한_정산에_한_사람은_한_행이며_부담_금액과_요청_횟수를_비울_수_없다() {
        Long room = finishedRoom(방장, 둘째);

        assertThatThrownBy(() -> insertTransfer(room, 둘째))
                .as("정산이 없으면 FK 위반").isInstanceOf(DataIntegrityViolationException.class);

        insertSplit(room, "OPEN");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO fare_split_transfer (match_id, user_id, request_count, created_at) VALUES (?, ?, 1, now())", room, 둘째))
                .as("부담 금액은 늘 채운다(NOT NULL)").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO fare_split_transfer (match_id, user_id, payment, created_at) VALUES (?, ?, 5000, now())", room, 둘째))
                .as("요청 횟수는 첫 송금 요청부터 늘 채운다(NOT NULL)").isInstanceOf(DataIntegrityViolationException.class);
        insertTransfer(room, 둘째);

        assertThatThrownBy(() -> insertTransfer(room, 둘째))
                .as("(match_id, user_id) PK").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTransfer(room, 방장))
                .as("받는 사람의 줄도 한 정산에 하나다").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void V39_정산의_1인당_금액_컬럼과_송금의_마지막_요청_입금_확인_요청_시각_컬럼은_없다() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE (table_name = 'fare_split' AND column_name = 'amount_per_person')
                    OR (table_name = 'fare_split_transfer' AND column_name IN ('last_requested_at', 'confirm_requested_at'))
                """, Integer.class)).isZero();
    }

    @Test
    void 같은_방에_정산을_두_번_INSERT_하면_덮어쓰지_않고_PK_위반을_중복_키로_알린다() {
        Long room = finishedRoom(방장, 둘째);
        RideRoster party = new RideRoster(room, 방장, true, List.of(방장, 둘째), List.of(방장, 둘째));
        tx.executeWithoutResult(status -> fareSplits.create(FareSplit.create(party, 방장, 10_000, Bank.KB, AccountNumber.of(RAW_ACCOUNT),
                List.of(new FarePayment(방장, 4_000), new FarePayment(둘째, 6_000)), Instant.now())));

        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                fareSplits.create(FareSplit.create(party, 방장, 20_000, Bank.KB, AccountNumber.of(RAW_ACCOUNT), List.of(new FarePayment(방장, 9_000), new FarePayment(둘째, 11_000)), Instant.now()))))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT total_fare FROM fare_split WHERE match_id = ?", Integer.class, room)).isEqualTo(10_000);
        assertThat(paymentOf(room, 방장)).isEqualTo(4_000);
        assertThat(paymentOf(room, 둘째)).isEqualTo(6_000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split_transfer WHERE match_id = ?", Integer.class, room)).isEqualTo(2);
    }

    // ───────────────────────── 계좌번호 암호화 ─────────────────────────

    @Test
    void 계좌번호는_암호문으로_저장되고_읽으면_숫자만_남은_평문으로_돌아온다() {
        Long room = finishedRoom(방장, 둘째, 셋째);

        service.create(room, 방장, request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_220), pay(둘째, 6_240), pay(셋째, 6_240)));

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
    void 사람마다_다른_금액으로_정산을_시작하면_방장의_줄까지_각자_부담_금액이_남고_시작_상세_수정_응답은_방장을_뺀_송금을_방_합류_순으로_준다() {
        // userId 순(받는사람 < 앞번호 < 뒷번호)과 다르게 뒷번호가 앞번호보다 먼저 들어왔다
        Long 받는사람 = newUser();
        Long 앞번호 = newUser();
        Long 뒷번호 = newUser();
        Long room = finishedRoom(받는사람, 뒷번호, 앞번호);
        List<UUID> 합류순 = List.of(publicIdOf(뒷번호), publicIdOf(앞번호));

        // 요청의 순서는 합류 순과 달라도 된다
        FareSplitDetailResponse created = service.create(room, 받는사람,
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(앞번호, 5_000), pay(받는사람, 6_700), pay(뒷번호, 7_000)));

        assertThat(created.myRole()).isEqualTo(FareSplitRole.PAYER);
        assertThat(created.status()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(created.myAmount()).as("받는 사람의 금액은 자기 줄의 부담 금액이다").isEqualTo(6_700);
        assertThat(created.tossDeeplink()).isNull();
        assertThat(created.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId).as("받는 사람은 송금 목록에 없다")
                .containsExactlyElementsOf(합류순);
        assertThat(created.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount).containsExactly(7_000, 5_000);
        assertThat(jdbc.queryForList("SELECT user_id FROM fare_split_transfer WHERE match_id = ? ORDER BY user_id", Long.class, room))
                .as("받는 사람의 줄도 장부에 남는다").containsExactly(받는사람, 앞번호, 뒷번호);
        assertThat(paymentOf(room, 받는사람)).as("부담 금액은 받는 사람이 넣은 대로 줄마다 둔다").isEqualTo(6_700);
        assertThat(paymentOf(room, 앞번호)).isEqualTo(5_000);
        assertThat(paymentOf(room, 뒷번호)).isEqualTo(7_000);
        assertThat(jdbc.queryForObject("SELECT sum(payment) FROM fare_split_transfer WHERE match_id = ?", Integer.class, room))
                .as("부담 금액의 합이 택시비다").isEqualTo(18_700);
        ArgumentCaptor<List<FarePayment>> 송금요청 = ArgumentCaptor.captor();
        verify(notifier).requestTransfer(eq(room), 송금요청.capture());
        assertThat(송금요청.getValue()).as("받는 사람을 뺀 보내는 사람마다 자기 금액").containsExactlyInAnyOrder(new FarePayment(앞번호, 5_000), new FarePayment(뒷번호, 7_000));
        assertThat(jdbc.queryForList("SELECT request_count FROM fare_split_transfer WHERE match_id = ? ORDER BY user_id", Integer.class, room))
                .as("받는 사람에게는 송금 요청을 보내지 않고(0번), 보내는 사람은 정산을 시작하며 보낸 첫 요청이 1번이다").containsExactly(0, 1, 1);
        Map<String, Object> 받는사람줄 = jdbc.queryForMap("SELECT sent_at, confirmed_at FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", room, 받는사람);
        assertThat(받는사람줄.get("sent_at")).as("받는 사람의 줄은 이미 낸 것이라 처음부터 CONFIRMED 다").isNotNull();
        assertThat(받는사람줄.get("confirmed_at")).isEqualTo(받는사람줄.get("sent_at"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split_transfer WHERE match_id = ? AND user_id <> ? AND (sent_at IS NOT NULL OR confirmed_at IS NOT NULL)",
                Integer.class, room, 받는사람)).as("보내는 사람의 줄은 NOT_SENT 로 시작한다").isZero();

        FareSplit found = tx.execute(status -> fareSplits.findByPartyId(room).orElseThrow());
        assertThat(found.getTransfers()).extracting(FareSplitTransfer::getMemberId).as("저장소는 방을 읽지 않고 userId 순으로 준다")
                .containsExactly(받는사람, 앞번호, 뒷번호);
        assertThat(found.findTransfer(받는사람).orElseThrow().status()).isEqualTo(TransferStatus.CONFIRMED);
        assertThat(found.findTransfer(받는사람).orElseThrow().getRequestCount()).isZero();
        assertThat(found.senderIds()).containsExactly(앞번호, 뒷번호);
        assertThat(service.getDetail(room, 받는사람).myAmount()).isEqualTo(6_700);
        assertThat(service.getDetail(room, 받는사람).transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId)
                .containsExactlyElementsOf(합류순);

        // 보내는 사람의 내 몫·토스 딥링크는 자기 송금의 금액이다
        FareSplitDetailResponse 앞번호상세 = service.getDetail(room, 앞번호);
        assertThat(앞번호상세.myRole()).isEqualTo(FareSplitRole.SENDER);
        assertThat(앞번호상세.myAmount()).isEqualTo(5_000);
        assertThat(앞번호상세.tossDeeplink()).isEqualTo(TossDeeplink.of(5_000, Bank.KAKAO, new AccountNumber(ACCOUNT_DIGITS)));
        assertThat(앞번호상세.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId).containsExactlyElementsOf(합류순);
        assertThat(앞번호상세.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount).containsExactly(7_000, 5_000);
        FareSplitDetailResponse 뒷번호상세 = service.getDetail(room, 뒷번호);
        assertThat(뒷번호상세.myAmount()).isEqualTo(7_000);
        assertThat(뒷번호상세.tossDeeplink()).isEqualTo(TossDeeplink.of(7_000, Bank.KAKAO, new AccountNumber(ACCOUNT_DIGITS)));

        FareSplitDetailResponse edited = service.edit(room, 받는사람,
                request(20_000, Bank.SHINHAN, "110-123-456789", pay(앞번호, 4_000), pay(뒷번호, 8_000), pay(받는사람, 8_000)));
        assertThat(edited.myAmount()).isEqualTo(8_000);
        assertThat(edited.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::publicId).containsExactlyElementsOf(합류순);
        assertThat(edited.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount).containsExactly(8_000, 4_000);

        // 필드의 방장은 이 방의 멤버가 아니다
        assertThatThrownBy(() -> service.getDetail(room, 방장))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    @Test
    void 방장이_아닌_탑승자가_정산을_시작하면_NOT_PARTY_HOST_이고_아무것도_남지_않는다() {
        Long room = finishedRoom(방장, 둘째, 셋째);

        // 둘째가 받는 사람인 것처럼 탑승자 전원의 금액을 넣었다
        FareSplitRequest request = request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_240), pay(둘째, 6_220), pay(셋째, 6_240));

        assertThatThrownBy(() -> service.create(room, 둘째, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.NOT_PARTY_HOST);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split WHERE match_id = ?", Integer.class, room)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split_transfer WHERE match_id = ?", Integer.class, room)).isZero();
    }

    @Test
    void 같은_방에서_정산을_두_번_시작하면_FARE_SPLIT_ALREADY_EXISTS_이고_처음_정산이_그대로_남는다() {
        Long room = finishedRoom(방장, 둘째, 셋째);
        service.create(room, 방장, request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_220), pay(둘째, 6_240), pay(셋째, 6_240)));

        FareSplitRequest again = request(20_000, Bank.SHINHAN, "110-123-456789", pay(방장, 6_660), pay(둘째, 6_670), pay(셋째, 6_670));

        assertThatThrownBy(() -> service.create(room, 방장, again))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_ALREADY_EXISTS);

        assertThat(jdbc.queryForMap("SELECT total_fare, bank_code FROM fare_split WHERE match_id = ?", room))
                .containsEntry("total_fare", 18_700).containsEntry("bank_code", "KAKAO");
        assertThat(jdbc.queryForList("SELECT payment FROM fare_split_transfer WHERE match_id = ? ORDER BY user_id", Integer.class, room))
                .containsExactly(6_220, 6_240, 6_240);
    }

    @Test
    void 부담_금액에_탑승자가_빠지거나_탑승자가_아닌_사람이_있으면_PAYMENT_TARGET_MISMATCH_이고_아무것도_남지_않는다() {
        Long 안온사람 = newUser();
        Long 방밖사람 = newUser();
        Long room = finishedRoom(List.of(방장, 둘째, 셋째), List.of(안온사람));
        List<FareSplitRequest> requests = List.of(
                // 셋째가 빠졌다 - 합은 택시비와 같다
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 9_350), pay(둘째, 9_350)),
                // 받는 사람(방장) 자신이 빠졌다 - 합은 택시비와 같다
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(둘째, 9_350), pay(셋째, 9_350)),
                // 도착하지 않아 타지 않은 멤버
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_120), pay(둘째, 6_240), pay(셋째, 6_240), pay(안온사람, 100)),
                // 방 밖 사람
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_120), pay(둘째, 6_240), pay(셋째, 6_240), pay(방밖사람, 100)),
                // 셋째 대신 방 밖 사람 - 사람 수는 맞다
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_220), pay(둘째, 6_240), pay(방밖사람, 6_240)),
                // 같은 사람이 두 번
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_220), pay(둘째, 3_000), pay(둘째, 3_240), pay(셋째, 6_240)));

        for(FareSplitRequest request : requests) {
            assertThatThrownBy(() -> service.create(room, 방장, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split WHERE match_id = ?", Integer.class, room)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split_transfer WHERE match_id = ?", Integer.class, room)).isZero();
        verify(notifier, never()).requestTransfer(any(), any());
    }

    @Test
    void 부담_금액의_합이_택시비보다_크거나_작으면_PAYMENTS_NOT_EQUAL_FARE_이고_아무것도_남지_않는다() {
        Long room = finishedRoom(방장, 둘째, 셋째);
        List<FareSplitRequest> requests = List.of(
                // 합이 1원 넘는다
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_220), pay(둘째, 6_240), pay(셋째, 6_241)),
                // 합이 1원 모자란다
                request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_220), pay(둘째, 6_240), pay(셋째, 6_239)));

        for(FareSplitRequest request : requests) {
            assertThatThrownBy(() -> service.create(room, 방장, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split WHERE match_id = ?", Integer.class, room)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split_transfer WHERE match_id = ?", Integer.class, room)).isZero();
        verify(notifier, never()).requestTransfer(any(), any());
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
            FareSplitRequest request = request(10_000, Bank.KB, RAW_ACCOUNT, pay(방장, 5_000), pay(둘째, 5_000));
            FareSplitDetailResponse created = pool.submit(() -> service.create(room, 방장, request)).get(10, TimeUnit.SECONDS);

            assertThat(created.partyId()).as("방 잠금이 풀리기 전에 만들어진다").isEqualTo(room);
            assertThat(holder.isDone()).as("방 잠금은 아직 잡혀 있다").isFalse();
        } finally {
            release.countDown();
        }
        holder.get(10, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT payer_id FROM fare_split WHERE match_id = ?", Long.class, room)).isEqualTo(방장);
    }

    @Test
    void 방장이_동시에_여러_번_정산을_시작해도_PK_로_하나만_만들어지고_나머지는_FARE_SPLIT_ALREADY_EXISTS() throws Exception {
        // 방 잠금 없이 PK 만으로 막으므로 선검사를 함께 통과한 INSERT 끼리 부딪히게 여러 방에서 되풀이한다
        for(int i = 0; i < 3; i++) {
            Long room = finishedRoom(방장, 둘째, 셋째);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();

            // 방장이 버튼을 연달아 눌렀다 - 요청마다 택시비를 달리해 어느 요청이 남았는지 가린다
            for(int totalFare : List.of(12_000, 15_000, 18_000, 21_000)) {
                FareSplitRequest request = request(totalFare, Bank.TOSS, RAW_ACCOUNT, pay(방장, totalFare / 3), pay(둘째, totalFare / 3), pay(셋째, totalFare / 3));
                Callable<Integer> create = () -> {
                    start.await();
                    service.create(room, 방장, request);
                    return totalFare;
                };
                futures.add(pool.submit(create));
            }
            start.countDown();

            List<Integer> winners = new ArrayList<>();
            int conflicted = 0;
            for(Future<Integer> future : futures) {
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
            int winner = winners.getFirst();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM fare_split WHERE match_id = ?", Integer.class, room)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT total_fare FROM fare_split WHERE match_id = ?", Integer.class, room)).isEqualTo(winner);
            assertThat(jdbc.queryForList("SELECT user_id FROM fare_split_transfer WHERE match_id = ?", Long.class, room))
                    .as("장부의 줄도 이긴 정산의 것만 남는다").containsExactlyInAnyOrder(방장, 둘째, 셋째);
            assertThat(jdbc.queryForList("SELECT payment FROM fare_split_transfer WHERE match_id = ?", Integer.class, room))
                    .containsOnly(winner / 3);
        }
    }

    // ───────────────────────── 송금 처리·완료 ─────────────────────────

    @Test
    void 보냈어요와_못_받았어요를_오가다_모두_확인되면_COMPLETED_로_닫히고_계좌번호_컬럼이_NULL_이_된다() {
        Long 받는사람 = newUser();
        Long 보낸사람 = newUser();
        Long 현금낸사람 = newUser();
        Long room = finishedRoom(받는사람, 보낸사람, 현금낸사람);
        service.create(room, 받는사람, request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(받는사람, 6_220), pay(보낸사람, 6_240), pay(현금낸사람, 6_240)));

        // 받는 사람의 줄은 송금이 아니라 보냈어요·받았어요·못 받았어요의 대상이 아니다
        assertThatThrownBy(() -> service.markSent(room, 받는사람))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.NOT_FARE_SPLIT_SENDER);
        assertThatThrownBy(() -> service.confirmReceived(room, 받는사람, publicIdOf(받는사람)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
        assertThatThrownBy(() -> service.markNotReceived(room, 받는사람, publicIdOf(받는사람)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
        assertThat(transferStatus(room, 받는사람)).isEqualTo(TransferStatus.CONFIRMED);

        service.markSent(room, 보낸사람);
        service.markSent(room, 보낸사람);
        assertThat(transferStatus(room, 보낸사람)).isEqualTo(TransferStatus.SENT);

        service.markNotReceived(room, 받는사람, publicIdOf(보낸사람));
        assertThat(jdbc.queryForObject("SELECT sent_at FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", java.sql.Timestamp.class, room, 보낸사람))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT request_count FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", Integer.class, room, 보낸사람))
                .as("못 받았어요는 송금 요청 횟수에 넣지 않는다").isEqualTo(1);
        assertThatThrownBy(() -> service.markNotReceived(room, 받는사람, publicIdOf(보낸사람)))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.TRANSFER_NOT_SENT);

        service.markSent(room, 보낸사람);
        service.confirmReceived(room, 받는사람, publicIdOf(보낸사람));
        service.confirmReceived(room, 받는사람, publicIdOf(보낸사람));
        assertThat(jdbc.queryForObject("SELECT status FROM fare_split WHERE match_id = ?", String.class, room))
                .as("받는 사람의 줄이 CONFIRMED 여도 보내는 사람이 남았으면 열려 있다").isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, room)).isNotNull();

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
        assertThat(detail.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::status)
                .containsExactly(TransferStatus.CONFIRMED, TransferStatus.CONFIRMED);
        assertThatThrownBy(() -> service.markSent(room, 보낸사람))
                .as("닫힌 정산은 이미 보낸 사람이 다시 눌러도 막는다")
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_CLOSED);
        assertThatThrownBy(() -> service.confirmReceived(room, 받는사람, publicIdOf(현금낸사람)))
                .as("닫힌 정산은 이미 확인한 송금을 다시 확인해도 막는다")
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_CLOSED);
    }

    @Test
    void 보냈어요를_누르면_커밋된_뒤에_받는_사람에게_입금_확인_요청이_나가고_다시_눌러도_더_나가지_않는다() {
        Long room = finishedRoom(방장, 둘째, 셋째);
        service.create(room, 방장, request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_220), pay(둘째, 6_240), pay(셋째, 6_240)));

        tx.executeWithoutResult(status -> {
            service.markSent(room, 둘째);
            verify(notifier, never()).requestConfirm(any(), any());
        });

        verify(notifier).requestConfirm(room, 방장);

        // 이미 SENT 면 저장도 알림도 없다
        service.markSent(room, 둘째);

        verify(notifier).requestConfirm(room, 방장);
    }

    @Test
    void 보냈어요가_롤백되면_입금_확인_요청도_나가지_않는다() {
        Long room = finishedRoom(방장, 둘째);
        service.create(room, 방장, request(10_000, Bank.KB, RAW_ACCOUNT, pay(방장, 5_000), pay(둘째, 5_000)));

        tx.executeWithoutResult(status -> {
            service.markSent(room, 둘째);
            status.setRollbackOnly();
        });

        verify(notifier, never()).requestConfirm(any(), any());
        assertThat(transferStatus(room, 둘째)).isEqualTo(TransferStatus.NOT_SENT);
    }

    // ───────────────────────── 수정 ─────────────────────────

    @Test
    void 아무도_보내기_전이면_수정이_방장의_줄까지_부담_금액을_새로_넣은_금액으로_바꾸고_새_계좌를_암호문으로_저장한다() {
        Long room = finishedRoom(방장, 둘째, 셋째);
        service.create(room, 방장, request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_220), pay(둘째, 6_240), pay(셋째, 6_240)));
        String before = jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, room);

        FareSplitDetailResponse edited = service.edit(room, 방장,
                request(20_000, Bank.SHINHAN, "110-123-456789", pay(방장, 6_500), pay(둘째, 8_000), pay(셋째, 5_500)));

        assertThat(edited.myAmount()).as("받는 사람의 금액도 새로 넣은 부담 금액이다").isEqualTo(6_500);
        assertThat(edited.transfers()).extracting(FareSplitDetailResponse.FareSplitTransferItem::amount).containsExactly(8_000, 5_500);
        assertThat(jdbc.queryForMap("SELECT total_fare, bank_code FROM fare_split WHERE match_id = ?", room))
                .containsEntry("total_fare", 20_000).containsEntry("bank_code", "SHINHAN");
        assertThat(paymentOf(room, 방장)).as("받는 사람의 줄은 CONFIRMED 여도 금액이 바뀐다").isEqualTo(6_500);
        assertThat(paymentOf(room, 둘째)).isEqualTo(8_000);
        assertThat(paymentOf(room, 셋째)).isEqualTo(5_500);
        assertThat(jdbc.queryForList("SELECT request_count FROM fare_split_transfer WHERE match_id = ? ORDER BY user_id", Integer.class, room))
                .as("수정은 송금 요청 횟수를 건드리지 않는다").containsExactly(0, 1, 1);
        assertThat(transferStatus(room, 방장)).as("수정해도 받는 사람의 줄은 CONFIRMED 그대로다").isEqualTo(TransferStatus.CONFIRMED);
        String after = jdbc.queryForObject("SELECT account_number FROM fare_split WHERE match_id = ?", String.class, room);
        assertThat(after).startsWith("v1:").isNotEqualTo(before).doesNotContain("110123456789");

        FareSplitDetailResponse sender = service.getDetail(room, 둘째);
        assertThat(sender.accountNumber()).isEqualTo("110123456789");
        assertThat(sender.myAmount()).isEqualTo(8_000);
        assertThat(sender.tossDeeplink()).isEqualTo(TossDeeplink.of(8_000, Bank.SHINHAN, new AccountNumber("110123456789")));
        assertThat(service.getDetail(room, 셋째).tossDeeplink()).isEqualTo(TossDeeplink.of(5_500, Bank.SHINHAN, new AccountNumber("110123456789")));
    }

    @Test
    void 수정하는_부담_금액이_참여자와_맞지_않거나_합이_택시비와_다르면_막히고_정산은_그대로_남는다() {
        Long 방밖사람 = newUser();
        Long room = finishedRoom(방장, 둘째, 셋째);
        service.create(room, 방장, request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_700), pay(둘째, 7_000), pay(셋째, 5_000)));
        List<FareSplitRequest> mismatched = List.of(
                // 셋째가 빠졌다 - 합은 택시비와 같다
                request(20_000, Bank.SHINHAN, "110-123-456789", pay(방장, 12_000), pay(둘째, 8_000)),
                // 받는 사람(방장) 자신이 빠졌다 - 합은 택시비와 같다
                request(20_000, Bank.SHINHAN, "110-123-456789", pay(둘째, 12_000), pay(셋째, 8_000)),
                // 셋째 대신 방 밖 사람
                request(20_000, Bank.SHINHAN, "110-123-456789", pay(방장, 6_500), pay(둘째, 8_000), pay(방밖사람, 5_500)),
                // 같은 사람이 두 번
                request(20_000, Bank.SHINHAN, "110-123-456789", pay(방장, 6_500), pay(둘째, 4_000), pay(둘째, 4_000), pay(셋째, 5_500)));

        for(FareSplitRequest request : mismatched) {
            assertThatThrownBy(() -> service.edit(room, 방장, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
        }
        // 합이 택시비보다 1원 크거나 작다
        for(int 셋째금액 : List.of(5_501, 5_499)) {
            FareSplitRequest request = request(20_000, Bank.SHINHAN, "110-123-456789", pay(방장, 6_500), pay(둘째, 8_000), pay(셋째, 셋째금액));

            assertThatThrownBy(() -> service.edit(room, 방장, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
        }

        assertThat(jdbc.queryForMap("SELECT total_fare, bank_code FROM fare_split WHERE match_id = ?", room))
                .containsEntry("total_fare", 18_700).containsEntry("bank_code", "KAKAO");
        assertThat(paymentOf(room, 방장)).isEqualTo(6_700);
        assertThat(paymentOf(room, 둘째)).isEqualTo(7_000);
        assertThat(paymentOf(room, 셋째)).isEqualTo(5_000);
    }

    @Test
    void 누군가_보냈다고_누른_뒤에는_수정이_FARE_SPLIT_ALREADY_STARTED_이고_그대로_남는다() {
        Long room = finishedRoom(방장, 둘째, 셋째);
        service.create(room, 방장, request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(방장, 6_220), pay(둘째, 6_240), pay(셋째, 6_240)));
        service.markSent(room, 셋째);

        FareSplitRequest request = request(20_000, Bank.SHINHAN, "110-123-456789", pay(방장, 6_660), pay(둘째, 6_670), pay(셋째, 6_670));

        assertThatThrownBy(() -> service.edit(room, 방장, request))
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_ALREADY_STARTED);

        assertThat(jdbc.queryForObject("SELECT total_fare FROM fare_split WHERE match_id = ?", Integer.class, room)).isEqualTo(18_700);
        assertThat(jdbc.queryForList("SELECT payment FROM fare_split_transfer WHERE match_id = ? ORDER BY user_id", Integer.class, room))
                .containsExactly(6_220, 6_240, 6_240);
    }

    // ───────────────────────── 내가 보낼 정산 ─────────────────────────

    @Test
    void 내가_보낼_정산은_내가_보내는_사람이고_아직_안_보낸_OPEN_정산만_최신순으로_주고_내가_받는_정산은_내_줄이_있어도_빠진다() {
        Long 나 = newUser();
        Long 받는사람 = newUser();
        Long 보낼방 = finishedRoom(받는사람, 나);
        Long 보낸방 = finishedRoom(받는사람, 나);
        Long 끝난방 = finishedRoom(받는사람, 나);
        Long 내가받는방 = finishedRoom(나, 받는사람);
        Long 최근방 = finishedRoom(받는사람, 나);
        service.create(보낼방, 받는사람, request(10_000, Bank.KB, RAW_ACCOUNT, pay(받는사람, 5_000), pay(나, 5_000)));
        service.create(보낸방, 받는사람, request(10_000, Bank.KB, RAW_ACCOUNT, pay(받는사람, 5_000), pay(나, 5_000)));
        service.create(끝난방, 받는사람, request(10_000, Bank.KB, RAW_ACCOUNT, pay(받는사람, 5_000), pay(나, 5_000)));
        service.create(내가받는방, 나, request(10_000, Bank.KB, RAW_ACCOUNT, pay(나, 5_000), pay(받는사람, 5_000)));
        service.create(최근방, 받는사람, request(12_000, Bank.KB, RAW_ACCOUNT, pay(받는사람, 4_500), pay(나, 7_500)));
        jdbc.update("UPDATE fare_split SET created_at = created_at - interval '1 hour' WHERE match_id = ?", 보낼방);
        service.markSent(보낸방, 나);
        service.confirmReceived(끝난방, 받는사람, publicIdOf(나));

        MyFareSplitListResponse response = service.findMine(나, TransferStatus.NOT_SENT);

        assertThat(response.fareSplits()).extracting(MyFareSplitListResponse.MyFareSplitItem::partyId).containsExactly(최근방, 보낼방);
        MyFareSplitListResponse.MyFareSplitItem first = response.fareSplits().get(0);
        assertThat(first.departure()).isEqualTo("광주송정역");
        assertThat(first.destination()).isEqualTo("전남대");
        assertThat(first.payer().publicId()).isEqualTo(publicIdOf(받는사람));
        assertThat(first.myAmount()).isEqualTo(7_500);
        assertThat(service.findMine(나, TransferStatus.SENT).fareSplits())
                .extracting(MyFareSplitListResponse.MyFareSplitItem::partyId).containsExactly(보낸방);
        assertThat(service.findMine(나, TransferStatus.CONFIRMED).fareSplits())
                .as("내가 받는 사람인 정산은 내 줄이 CONFIRMED 로 있어도 내가 보낼 정산이 아니다").isEmpty();
    }

    // ───────────────────────── 정산 행 잠금 ─────────────────────────

    @Test
    void 송금_처리는_정산_행_잠금을_기다렸다가_돈다() throws Exception {
        Long room = finishedRoom(방장, 둘째);
        service.create(room, 방장, request(10_000, Bank.KB, RAW_ACCOUNT, pay(방장, 5_000), pay(둘째, 5_000)));
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
            service.create(room, 받는사람, request(18_700, Bank.KAKAO, RAW_ACCOUNT, pay(받는사람, 6_220), pay(첫째, 6_240), pay(둘, 6_240)));
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
                    .as("받는 사람의 줄까지 셋 다 CONFIRMED").isEqualTo(3);
        }
    }

    // ───────────────────────── fixtures ─────────────────────────

    /** 넘긴 순서대로 합류하고 모두 도착해 출발한(FINISHED) 방. 합류 시각(created_at)도 그 순서로 박는다 */
    private Long finishedRoom(Long... memberIdsInJoinOrder) {
        return finishedRoom(List.of(memberIdsInJoinOrder), List.of());
    }

    /** 탑승자가 넘긴 순서대로 합류해 도착하고, absenteeIds 는 그 뒤에 합류했지만 도착하지 않은 채 출발한(FINISHED) 방. 방장은 맨 앞 탑승자다 */
    private Long finishedRoom(List<Long> riderIdsInJoinOrder, List<Long> absenteeIds) {
        Instant meetingAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofHours(1));
        List<Long> memberIdsInJoinOrder = new ArrayList<>(riderIdsInJoinOrder);
        memberIdsInJoinOrder.addAll(absenteeIds);
        List<PartyMember> members = new ArrayList<>();
        for(Long memberId : memberIdsInJoinOrder) {
            members.add(PartyMember.load(memberId, meetingAt, absenteeIds.contains(memberId) ? null : meetingAt));
        }

        Long id = parties.save(Party.load(null, DEPARTURE, DESTINATION, "광주송정역", "전남대", new Capacity(3), members,
                memberIdsInJoinOrder.getFirst(), meetingAt.minus(Duration.ofHours(1)), PartyStatus.FINISHED,
                12000, 25, "encoded-path", 0L, RoomType.REALTIME, meetingAt)).getId();
        rooms.add(id);

        for(int i = 0; i < memberIdsInJoinOrder.size(); i++) {
            jdbc.update("UPDATE user_match_room SET created_at = ? WHERE match_id = ? AND user_id = ?",
                    java.sql.Timestamp.from(meetingAt.minusSeconds(60 - i * 10L)), id, memberIdsInJoinOrder.get(i));
        }

        return id;
    }

    /** 받는 사람(방장)을 포함한 탑승자마다 부담 금액을 실은 정산 시작·수정 요청 */
    private static FareSplitRequest request(int totalFare, Bank bank, String accountNumber, FareSplitRequest.Payment... payments) {
        return new FareSplitRequest(totalFare, bank, accountNumber, List.of(payments));
    }

    /** userId 가 amount 를 부담한다 - 요청은 사람을 publicId 로 가리킨다 */
    private FareSplitRequest.Payment pay(Long userId, int amount) {
        return new FareSplitRequest.Payment(publicIdOf(userId), amount);
    }

    private int paymentOf(Long room, Long userId) {
        return jdbc.queryForObject("SELECT payment FROM fare_split_transfer WHERE match_id = ? AND user_id = ?", Integer.class, room, userId);
    }

    /** 정산 요청·송금 처리(받았어요·못 받았어요)가 publicId 로 사람을 가리키고 응답 송금도 publicId(합류 순)로 확인해서 쓰는 실제 users 행 */
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

    /** 방장이 받는 사람인 택시비 1만 원 정산과 받는 사람의 줄(5천 원 부담, 처음부터 CONFIRMED, 요청 0번) */
    private void insertSplit(Long room, String status) {
        jdbc.update("""
                INSERT INTO fare_split (match_id, payer_id, total_fare, bank_code, account_number, status, created_at)
                VALUES (?, ?, 10000, 'KB', NULL, ?, now())
                """, room, 방장, status);
        jdbc.update("""
                INSERT INTO fare_split_transfer (match_id, user_id, payment, sent_at, confirmed_at, request_count, created_at)
                VALUES (?, ?, 5000, now(), now(), 0, now())
                """, room, 방장);
    }

    /** 보내는 사람의 줄(5천 원 부담, NOT_SENT, 첫 요청 1번) */
    private void insertTransfer(Long room, Long userId) {
        jdbc.update("INSERT INTO fare_split_transfer (match_id, user_id, payment, request_count, created_at) VALUES (?, ?, 5000, 1, now())", room, userId);
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
