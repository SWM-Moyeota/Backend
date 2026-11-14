package team.codingforest.moyeota.faresplit.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.FarePayment;
import team.codingforest.moyeota.faresplit.domain.FareShare;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotice;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.faresplit.domain.RideRoster;
import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

/**
 *  H2 에서 엔티티 매핑(암호화 컨버터, 복합키 송금 행의 사람마다 다른 부담 금액·요청 횟수, 받는 사람의 줄)과 송금 순서(userId 순), 변경 저장, 내가 보낼 정산 목록 쿼리,
 *  스케줄러 대상 조회(재요청·만료)와 받는 사람별 OPEN 정산 조회, 탑승 기록용 정산 금액 조회를 본다.
 *  저장소는 방을 읽지 않아(PartyAccess 빈 없이 뜬다) 송금을 방 합류 순이 아니라 userId 순으로 돌려준다 - 응답의 합류 순은 FareSplitServiceUnitTest·FareSplitPostgresTest 가 본다.
 *  마이그레이션·제약·행 잠금은 FareSplitPostgresTest·FareSplitSweepPostgresTest 가 본다
 */
@DataJpaTest
@Import({FareSplitJpa.class, JpaAuditingConfig.class})
class FareSplitJpaPersistenceTest {
    private static final Long PAYER_ID = 11L;
    private static final Long FIRST_ID = 12L;
    private static final Long SECOND_ID = 13L;
    private static final Long THIRD_ID = 14L;
    private static final Long OUTSIDER_ID = 99L;
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");
    private static final AccountNumber NEW_ACCOUNT = new AccountNumber("110123456789");
    // 한국 시각 11-05 12:00
    private static final Instant SWEEP_AT = Instant.parse("2026-11-05T03:00:00Z");

    private final FareSplitJpa fareSplits;
    private final TestEntityManager em;
    private long nextPartyId = 1_000L;

    @Autowired
    FareSplitJpaPersistenceTest(FareSplitJpa fareSplits, TestEntityManager em) {
        this.fareSplits = fareSplits;
        this.em = em;
    }

    @DisplayName("create - 정산과 탑승자 전원의 송금 행(사람마다 다른 부담 금액, 보내는 사람은 요청 횟수 1, 받는 사람은 시작 시각에 CONFIRMED·요청 횟수 0)을 저장하고 다시 읽으면 같은 값이 돌아온다")
    @Test
    void create() {

        /*
         * settings
         */
        RideRoster party = finishedPartyHostedBy(PAYER_ID, SECOND_ID, PAYER_ID, FIRST_ID);
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        FareSplit split = FareSplit.create(party, PAYER_ID, 18_700, Bank.SHINHAN, ACCOUNT,
                List.of(pay(PAYER_ID, 6_700), pay(FIRST_ID, 7_000), pay(SECOND_ID, 5_000)), startedAt);

        /*
         * test
         */
        FareSplit created = fareSplits.create(split);
        clearContext();
        FareSplit found = fareSplits.findByPartyId(party.partyId()).orElseThrow();

        /*
         * verify
         */
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(found.getPartyId()).isEqualTo(party.partyId());
        assertThat(found.getPayerId()).isEqualTo(PAYER_ID);
        assertThat(found.getTotalFare()).isEqualTo(18_700);
        assertThat(found.payments()).as("받는 사람을 포함한 전원, userId 순").containsExactly(pay(PAYER_ID, 6_700), pay(FIRST_ID, 7_000), pay(SECOND_ID, 5_000));
        assertThat(found.payments().stream().mapToInt(FarePayment::amount).sum()).as("부담 금액의 합이 택시비").isEqualTo(18_700);
        assertThat(found.senderPayments()).containsExactly(pay(FIRST_ID, 7_000), pay(SECOND_ID, 5_000));
        assertThat(found.amountOf(PAYER_ID)).isEqualTo(6_700);
        assertThat(found.amountOf(FIRST_ID)).isEqualTo(7_000);
        assertThat(found.amountOf(SECOND_ID)).isEqualTo(5_000);
        assertThat(found.getBank()).isEqualTo(Bank.SHINHAN);
        assertThat(found.getAccountNumber()).isEqualTo(ACCOUNT);
        assertThat(found.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(found.getClosedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getTransfers()).hasSize(3);
        assertThat(found.senderTransfers()).hasSize(2).allSatisfy(t -> {
            assertThat(t.status()).isEqualTo(TransferStatus.NOT_SENT);
            assertThat(t.getRequestCount()).as("시작할 때 보낸 첫 송금 요청").isEqualTo(1);
        });
        FareSplitTransfer payerRow = found.findTransfer(PAYER_ID).orElseThrow();
        assertThat(payerRow.status()).as("받는 사람은 이미 낸 것이다").isEqualTo(TransferStatus.CONFIRMED);
        assertThat(payerRow.getSentAt()).isEqualTo(startedAt);
        assertThat(payerRow.getConfirmedAt()).isEqualTo(startedAt);
        assertThat(payerRow.getRequestCount()).as("받는 사람에게는 송금 요청을 보내지 않는다").isZero();
        assertThat(rawPaymentsAndRequestCounts(party.partyId())).as("payment·request_count 컬럼, userId 순(받는 사람의 줄이 먼저)")
                .containsExactly(List.of(6_700, 0), List.of(7_000, 1), List.of(5_000, 1));
        assertThat(rawPaidRowCount(party.partyId(), PAYER_ID, 6_700, startedAt)).as("받는 사람의 줄 - payment = 부담금, sent_at = confirmed_at = 시작 시각, request_count = 0").isEqualTo(1);
        assertThat(rawPaidRowCount(party.partyId(), FIRST_ID, 7_000, startedAt)).as("보내는 사람의 줄은 시각이 비어 있다").isZero();
    }

    @DisplayName("create - 계좌번호는 암호문으로 저장된다")
    @Test
    void create_encryptsAccountNumber() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID);

        /*
         * test
         */
        fareSplits.create(FareSplit.create(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(FIRST_ID, 5_000)), Instant.now()));
        clearContext();

        /*
         * verify
         */
        String stored = (String) em.getEntityManager()
                .createNativeQuery("SELECT account_number FROM fare_split WHERE match_id = :id")
                .setParameter("id", party.partyId())
                .getSingleResult();
        assertThat(stored).startsWith("v1:").doesNotContain(ACCOUNT.value());
    }

    @DisplayName("create / findByPartyId - 송금은 받는 사람의 줄을 포함해 방 합류 순이 아니라 userId 순으로 담고(방을 읽지 않는다) 금액은 그 사람 것이 따라간다")
    @Test
    void findByPartyId_userIdOrder() {

        /*
         * settings
         */
        RideRoster party = finishedPartyHostedBy(PAYER_ID, SECOND_ID, PAYER_ID, FIRST_ID);

        /*
         * test
         */
        FareSplit created = fareSplits.create(FareSplit.create(party, PAYER_ID, 12_000, Bank.KB, ACCOUNT,
                List.of(pay(SECOND_ID, 4_000), pay(PAYER_ID, 5_000), pay(FIRST_ID, 3_000)), Instant.now()));
        clearContext();
        FareSplit found = fareSplits.findByPartyId(party.partyId()).orElseThrow();

        /*
         * verify
         */
        assertThat(created.getTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(PAYER_ID, FIRST_ID, SECOND_ID);
        assertThat(created.getTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(5_000, 3_000, 4_000);
        assertThat(found.getTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(PAYER_ID, FIRST_ID, SECOND_ID);
        assertThat(found.getTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(5_000, 3_000, 4_000);
        assertThat(found.senderTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(FIRST_ID, SECOND_ID);
    }

    @DisplayName("findByPartyId - 정산이 없는 방이면 empty")
    @Test
    void findByPartyId_none() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID);

        /*
         * test
         */
        Optional<FareSplit> found = fareSplits.findByPartyId(party.partyId());

        /*
         * verify
         */
        assertThat(found).isEmpty();
    }

    @DisplayName("create - 같은 방에 두 번 만들면 덮어쓰지 않고 중복 키(DuplicateKeyException)로 알린다")
    @Test
    void create_duplicate() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.create(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(FIRST_ID, 5_000)), Instant.now()));
        clearContext();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> fareSplits.create(
                FareSplit.create(party, PAYER_ID, 20_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 8_000), pay(FIRST_ID, 12_000)), Instant.now())));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(DuplicateKeyException.class);
        clearContextQuietly();
        FareSplit kept = fareSplits.findByPartyId(party.partyId()).orElseThrow();
        assertThat(kept.getTotalFare()).as("처음 만든 정산이 남는다").isEqualTo(10_000);
        assertThat(kept.payments()).as("처음 넣은 금액이 남는다").containsExactly(pay(PAYER_ID, 5_000), pay(FIRST_ID, 5_000));
    }

    // ───────────────────────── findByPartyIdForUpdate ─────────────────────────

    @DisplayName("findByPartyIdForUpdate - 잠가 읽어도 받는 사람의 줄을 포함한 송금을 userId 순으로 각자 금액과 함께 담고, 정산이 없으면 empty")
    @Test
    void findByPartyIdForUpdate() {

        /*
         * settings
         */
        RideRoster party = finishedPartyHostedBy(PAYER_ID, SECOND_ID, PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.create(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 3_000), pay(FIRST_ID, 2_500), pay(SECOND_ID, 4_500)), Instant.now()));
        clearContext();

        /*
         * test
         */
        FareSplit found = fareSplits.findByPartyIdForUpdate(party.partyId()).orElseThrow();

        /*
         * verify
         */
        assertThat(found.getTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(PAYER_ID, FIRST_ID, SECOND_ID);
        assertThat(found.getTransfers()).extracting(FareSplitTransfer::getPayment).containsExactly(3_000, 2_500, 4_500);
        assertThat(found.getAccountNumber()).isEqualTo(ACCOUNT);
        assertThat(fareSplits.findByPartyIdForUpdate(party.partyId() + 1_000)).isEmpty();
    }

    // ───────────────────────── save ─────────────────────────

    @DisplayName("save - 송금 시각을 옮기고, 보내는 사람이 모두 확인돼 닫히면 상태·닫힌 시각과 계좌번호 삭제(NULL)를 옮긴다")
    @Test
    void save_transfersAndCompletion() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, List.of(pay(PAYER_ID, 6_220), pay(FIRST_ID, 6_240), pay(SECOND_ID, 6_240)), Instant.now()));
        clearContext();
        Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant confirmedAt = sentAt.plusSeconds(60);
        Instant payerConfirmedAt = fareSplits.findByPartyId(party.partyId()).orElseThrow().findTransfer(PAYER_ID).orElseThrow().getConfirmedAt();
        clearContext();

        /*
         * test
         */
        FareSplit split = fareSplits.findByPartyIdForUpdate(party.partyId()).orElseThrow();
        split.markSent(FIRST_ID, sentAt);
        split.confirmReceived(PAYER_ID, FIRST_ID, confirmedAt);
        split.confirmReceived(PAYER_ID, SECOND_ID, confirmedAt);
        fareSplits.save(split);
        clearContext();

        /*
         * verify
         */
        FareSplit found = fareSplits.findByPartyId(party.partyId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(FareSplitStatus.COMPLETED);
        assertThat(found.getClosedAt()).isEqualTo(confirmedAt);
        assertThat(found.getAccountNumber()).isNull();
        FareSplitTransfer first = found.findTransfer(FIRST_ID).orElseThrow();
        assertThat(first.getSentAt()).isEqualTo(sentAt);
        assertThat(first.getConfirmedAt()).isEqualTo(confirmedAt);
        FareSplitTransfer second = found.findTransfer(SECOND_ID).orElseThrow();
        assertThat(second.getSentAt()).isNull();
        assertThat(second.getConfirmedAt()).isEqualTo(confirmedAt);
        FareSplitTransfer payerRow = found.findTransfer(PAYER_ID).orElseThrow();
        assertThat(payerRow.getConfirmedAt()).as("받는 사람의 줄은 시작할 때 확인된 그대로다").isEqualTo(payerConfirmedAt);
        assertThat(payerRow.getPayment()).isEqualTo(6_220);
        assertThat(rawAccountNumber(party.partyId())).isNull();
    }

    @DisplayName("save - 못 받음으로 되돌리면 보낸 시각이 지워지고 요청 횟수는 그대로다")
    @Test
    void save_notReceived() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.create(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(FIRST_ID, 5_000)), Instant.now()));
        clearContext();
        FareSplit sent = fareSplits.findByPartyIdForUpdate(party.partyId()).orElseThrow();
        sent.markSent(FIRST_ID, Instant.now());
        fareSplits.save(sent);
        clearContext();

        /*
         * test
         */
        FareSplit split = fareSplits.findByPartyIdForUpdate(party.partyId()).orElseThrow();
        split.markNotReceived(PAYER_ID, FIRST_ID);
        fareSplits.save(split);
        clearContext();

        /*
         * verify
         */
        FareSplitTransfer transfer = fareSplits.findByPartyId(party.partyId()).orElseThrow().findTransfer(FIRST_ID).orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(transfer.getSentAt()).isNull();
        assertThat(transfer.getRequestCount()).isEqualTo(1);
    }

    @DisplayName("save - 고친 택시비·은행·계좌번호와 받는 사람을 포함해 사람마다 다시 정한 부담 금액을 옮기고(요청 횟수는 그대로) 계좌번호는 다시 암호문으로 저장된다")
    @Test
    void save_edit() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, List.of(pay(PAYER_ID, 6_220), pay(FIRST_ID, 6_240), pay(SECOND_ID, 6_240)), Instant.now()));
        clearContext();

        /*
         * test
         */
        FareSplit split = fareSplits.findByPartyIdForUpdate(party.partyId()).orElseThrow();
        split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT, List.of(pay(PAYER_ID, 6_500), pay(FIRST_ID, 9_000), pay(SECOND_ID, 4_500)));
        fareSplits.save(split);
        clearContext();

        /*
         * verify
         */
        FareSplit found = fareSplits.findByPartyId(party.partyId()).orElseThrow();
        assertThat(found.getTotalFare()).isEqualTo(20_000);
        assertThat(found.payments()).containsExactly(pay(PAYER_ID, 6_500), pay(FIRST_ID, 9_000), pay(SECOND_ID, 4_500));
        assertThat(found.amountOf(PAYER_ID)).as("받는 사람 줄의 금액도 바뀐다").isEqualTo(6_500);
        assertThat(found.findTransfer(PAYER_ID).orElseThrow().status()).as("받는 사람의 줄은 그대로 CONFIRMED").isEqualTo(TransferStatus.CONFIRMED);
        assertThat(rawPaymentsAndRequestCounts(party.partyId())).as("받는 사람의 줄(6,220 → 6,500, 요청 횟수 0)이 먼저")
                .containsExactly(List.of(6_500, 0), List.of(9_000, 1), List.of(4_500, 1));
        assertThat(found.getBank()).isEqualTo(Bank.SHINHAN);
        assertThat(found.getAccountNumber()).isEqualTo(NEW_ACCOUNT);
        assertThat(found.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(rawAccountNumber(party.partyId())).startsWith("v1:").doesNotContain(NEW_ACCOUNT.value());
    }

    // ───────────────────────── findOpenTransfers ─────────────────────────

    @DisplayName("findOpenTransfers - 내가 보내는 사람인 OPEN 정산만(내가 받는 사람인 정산은 제외) 받는 사람·내가 보낼 금액(같은 정산의 다른 사람 금액이 아니다)·내 송금 시각과 함께 시작 최신순으로 준다")
    @Test
    void findOpenTransfers() {

        /*
         * settings
         */
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
        RideRoster older = finishedParty(PAYER_ID, FIRST_ID);
        RideRoster newer = finishedParty(SECOND_ID, FIRST_ID, THIRD_ID);
        RideRoster completed = finishedParty(PAYER_ID, FIRST_ID);
        RideRoster mine = finishedParty(FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.create(older, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 6_700), pay(FIRST_ID, 3_300)), base));
        fareSplits.create(FareSplit.create(newer, SECOND_ID, 12_000, Bank.KB, ACCOUNT, List.of(pay(SECOND_ID, 2_500), pay(FIRST_ID, 7_500), pay(THIRD_ID, 2_000)), base));
        fareSplits.create(FareSplit.create(completed, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(FIRST_ID, 5_000)), base));
        fareSplits.create(FareSplit.create(mine, FIRST_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(FIRST_ID, 6_000), pay(SECOND_ID, 4_000)), base));
        clearContext();
        setCreatedAt(older.partyId(), base.minus(Duration.ofHours(2)));
        setCreatedAt(newer.partyId(), base.minus(Duration.ofHours(1)));
        FareSplit sent = fareSplits.findByPartyIdForUpdate(newer.partyId()).orElseThrow();
        sent.markSent(FIRST_ID, base);
        fareSplits.save(sent);
        FareSplit done = fareSplits.findByPartyIdForUpdate(completed.partyId()).orElseThrow();
        done.confirmReceived(PAYER_ID, FIRST_ID, base);
        fareSplits.save(done);
        clearContext();

        /*
         * test
         */
        List<OpenTransfer> transfers = fareSplits.findOpenTransfers(FIRST_ID);

        /*
         * verify
         */
        assertThat(transfers).extracting(OpenTransfer::partyId).as("내가 받는 사람인 정산(mine)은 내 줄이 있어도 나오지 않는다")
                .containsExactly(newer.partyId(), older.partyId());
        OpenTransfer first = transfers.get(0);
        assertThat(first.payerId()).isEqualTo(SECOND_ID);
        assertThat(first.payment()).as("같이 보내는 사람(2,000원)·받는 사람(2,500원)이 아니라 내 금액").isEqualTo(7_500);
        assertThat(first.sentAt()).isEqualTo(base);
        assertThat(first.confirmedAt()).isNull();
        assertThat(first.status()).isEqualTo(TransferStatus.SENT);
        assertThat(first.createdAt()).isEqualTo(base.minus(Duration.ofHours(1)));
        assertThat(transfers.get(1).status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(transfers.get(1).payerId()).isEqualTo(PAYER_ID);
        assertThat(transfers.get(1).payment()).isEqualTo(3_300);
        assertThat(fareSplits.findOpenTransfers(THIRD_ID)).extracting(OpenTransfer::payment).containsExactly(2_000);
        assertThat(fareSplits.findOpenTransfers(SECOND_ID)).as("받는 사람인 정산(newer)은 빼고 보내는 사람인 정산(mine)만")
                .extracting(OpenTransfer::partyId, OpenTransfer::payerId, OpenTransfer::payment)
                .containsExactly(tuple(mine.partyId(), FIRST_ID, 4_000));
        assertThat(fareSplits.findOpenTransfers(PAYER_ID)).as("받는 사람으로만 있는 정산은 자기 줄(CONFIRMED)이 있어도 나오지 않는다").isEmpty();
    }

    // ───────────────────────── 요청 횟수 저장 ─────────────────────────

    @DisplayName("save - 재요청으로 올라간 요청 횟수를 옮긴다(보냈다고 누른 송금은 그대로)")
    @Test
    void save_requestCount() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID, SECOND_ID);
        Instant createdAt = Instant.parse("2026-11-03T03:00:00Z");
        fareSplits.create(FareSplit.create(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, List.of(pay(PAYER_ID, 6_700), pay(FIRST_ID, 7_000), pay(SECOND_ID, 5_000)), createdAt));
        clearContext();
        setCreatedAt(party.partyId(), createdAt);
        clearContext();

        /*
         * test
         */
        FareSplit split = fareSplits.findByPartyIdForUpdate(party.partyId()).orElseThrow();
        split.markSent(SECOND_ID, createdAt.plus(Duration.ofMinutes(5)));
        FareSplitNotice notice = split.remindTransfers(createdAt.plus(Duration.ofHours(4)));
        fareSplits.save(split);
        clearContext();

        /*
         * verify
         */
        assertThat(notice.payments()).as("재요청을 받을 사람과 저장된 그 사람 금액").containsExactly(pay(FIRST_ID, 7_000));
        FareSplit found = fareSplits.findByPartyId(party.partyId()).orElseThrow();
        assertThat(found.findTransfer(FIRST_ID).orElseThrow().getRequestCount()).isEqualTo(2);
        assertThat(found.findTransfer(SECOND_ID).orElseThrow().getRequestCount()).as("보냈다고 눌러 재요청하지 않았다").isEqualTo(1);
        assertThat(found.findTransfer(PAYER_ID).orElseThrow().getRequestCount()).as("받는 사람은 재요청 대상이 아니다").isZero();
        assertThat(rawPaymentsAndRequestCounts(party.partyId())).as("요청 횟수만 오르고 금액은 그대로, 받는 사람의 줄은 0 그대로")
                .containsExactly(List.of(6_700, 0), List.of(7_000, 2), List.of(5_000, 1));
    }

    // ───────────────────────── 스케줄러 대상 조회 ─────────────────────────

    @DisplayName("findTransferRequestTargets - 아직 안 보낸 송금의 차례(시작 + 요청 횟수 × 4시간, 경계 포함)가 된 OPEN·계좌 있는 정산만 방 번호 순으로 주고(받는 사람의 줄은 CONFIRMED 라 대상을 만들지 않는다), 도메인 판단과 같다")
    @Test
    void findTransferRequestTargets() {

        /*
         * settings
         */
        Long firstDue = splitCreatedAt(hoursBeforeSweep(4));
        Long firstNotYet = splitCreatedAt(hoursBeforeSweep(4).plusSeconds(1));
        Long late = splitCreatedAt(hoursBeforeSweep(13));                                  // 서버가 멈춰 차례가 밀렸다
        Long secondDue = splitRequested(2, hoursBeforeSweep(8));
        Long secondNotYet = splitRequested(2, hoursBeforeSweep(8).plusSeconds(1));         // 시작 4시간은 지났지만 두 번 받아 8시간을 기다린다
        Long thirdDue = splitRequested(3, hoursBeforeSweep(12));
        Long fourthDue = splitRequested(4, hoursBeforeSweep(16));
        Long fifthDue = splitRequested(5, hoursBeforeSweep(20));
        Long fifthNotYet = splitRequested(5, hoursBeforeSweep(20).plusSeconds(1));
        Long maxed = splitRequested(FareSplit.MAX_TRANSFER_REQUESTS, hoursBeforeSweep(23)); // 상한 6번을 다 보냈다
        Long sentOnly = splitCreatedAt(hoursBeforeSweep(5));
        execute("UPDATE fare_split_transfer SET sent_at = ?1 WHERE match_id = ?2 AND user_id = ?3", hoursBeforeSweep(1), sentOnly, FIRST_ID);
        Long confirmedOnly = splitCreatedAt(hoursBeforeSweep(5));
        execute("UPDATE fare_split_transfer SET confirmed_at = ?1 WHERE match_id = ?2 AND user_id = ?3", SWEEP_AT, confirmedOnly, FIRST_ID);
        Long sentAndConfirmed = threeRiderSplitCreatedAt(hoursBeforeSweep(5));              // 보내는 사람이 SENT·CONFIRMED 뿐이다 - 받는 사람의 줄 때문에 잡히면 안 된다
        execute("UPDATE fare_split_transfer SET sent_at = ?1 WHERE match_id = ?2 AND user_id = ?3", hoursBeforeSweep(1), sentAndConfirmed, FIRST_ID);
        execute("UPDATE fare_split_transfer SET confirmed_at = ?1 WHERE match_id = ?2 AND user_id = ?3", SWEEP_AT, sentAndConfirmed, SECOND_ID);
        Long expired = splitCreatedAt(hoursBeforeSweep(5));
        execute("UPDATE fare_split SET status = 'EXPIRED', account_number = NULL WHERE match_id = ?1", expired);
        Long noAccount = splitCreatedAt(hoursBeforeSweep(5));
        execute("UPDATE fare_split SET account_number = NULL WHERE match_id = ?1", noAccount);
        clearContext();

        /*
         * test
         */
        List<Long> targets = fareSplits.findTransferRequestTargets(SWEEP_AT);

        /*
         * verify
         */
        assertThat(targets).containsExactly(firstDue, late, secondDue, thirdDue, fourthDue, fifthDue);
        for(Long partyId : List.of(firstDue, firstNotYet, late, secondDue, secondNotYet, thirdDue, fourthDue, fifthDue, fifthNotYet, maxed,
                sentOnly, confirmedOnly, sentAndConfirmed, expired, noAccount)) {
            boolean shouldSend = fareSplits.findByPartyId(partyId).orElseThrow().remindTransfers(SWEEP_AT).shouldSend();
            assertThat(shouldSend).as("partyId=%d 의 쿼리 결과와 도메인 판단", partyId).isEqualTo(targets.contains(partyId));
        }
    }

    @DisplayName("findTransferRequestTargets - 한 정산에 차례가 된 송금이 여럿(요청 횟수가 달라도)이어도 한 번만 주고, 보낸 사람이 섞여 있어도 안 보낸 사람이 차례면 준다")
    @Test
    void findTransferRequestTargets_distinct() {

        /*
         * settings
         */
        Long sameCount = threeRiderSplitCreatedAt(hoursBeforeSweep(9));
        Long differentCounts = threeRiderSplitCreatedAt(hoursBeforeSweep(9));
        execute("UPDATE fare_split_transfer SET request_count = 2 WHERE match_id = ?1 AND user_id = ?2", differentCounts, SECOND_ID);
        Long oneSent = threeRiderSplitCreatedAt(hoursBeforeSweep(9));
        execute("UPDATE fare_split_transfer SET sent_at = ?1 WHERE match_id = ?2 AND user_id = ?3", hoursBeforeSweep(1), oneSent, FIRST_ID);
        Long dueOneSent = threeRiderSplitCreatedAt(hoursBeforeSweep(5));
        execute("UPDATE fare_split_transfer SET request_count = 2 WHERE match_id = ?1 AND user_id = ?2", dueOneSent, SECOND_ID);
        execute("UPDATE fare_split_transfer SET sent_at = ?1 WHERE match_id = ?2 AND user_id = ?3", hoursBeforeSweep(1), dueOneSent, FIRST_ID);
        clearContext();

        /*
         * test
         */
        List<Long> targets = fareSplits.findTransferRequestTargets(SWEEP_AT);

        /*
         * verify
         */
        assertThat(targets).as("마지막 정산은 차례가 된 사람(요청 1번)이 이미 보냈고, 안 보낸 사람(요청 2번)은 8시간이 안 됐다")
                .containsExactly(sameCount, differentCounts, oneSent);
        assertThat(fareSplits.findByPartyId(differentCounts).orElseThrow().remindTransfers(SWEEP_AT).recipientIds()).containsExactly(FIRST_ID, SECOND_ID);
        assertThat(fareSplits.findByPartyId(sameCount).orElseThrow().remindTransfers(SWEEP_AT).payments())
                .containsExactly(pay(FIRST_ID, 3_000), pay(SECOND_ID, 3_000));
        assertThat(fareSplits.findByPartyId(oneSent).orElseThrow().remindTransfers(SWEEP_AT).recipientIds()).containsExactly(SECOND_ID);
        assertThat(fareSplits.findByPartyId(dueOneSent).orElseThrow().remindTransfers(SWEEP_AT).shouldSend()).isFalse();
    }

    @DisplayName("findExpireTargets - 시작하고 1일이 지난(경계 포함) OPEN 정산만 오래된 순으로 준다")
    @Test
    void findExpireTargets() {

        /*
         * settings
         */
        Instant dayAgo = SWEEP_AT.minus(Duration.ofDays(1));
        Long older = splitCreatedAt(dayAgo.minus(Duration.ofDays(1)));
        Long exactlyDay = splitCreatedAt(dayAgo);
        Long notYet = splitCreatedAt(dayAgo.plusSeconds(1));
        Long completed = splitCreatedAt(dayAgo.minus(Duration.ofDays(1)));
        execute("UPDATE fare_split SET status = 'COMPLETED', account_number = NULL WHERE match_id = ?1", completed);
        clearContext();

        /*
         * test
         */
        List<Long> targets = fareSplits.findExpireTargets(SWEEP_AT);

        /*
         * verify
         */
        assertThat(targets).containsExactly(older, exactlyDay);
        assertThat(fareSplits.findByPartyId(notYet).orElseThrow().expire(SWEEP_AT)).isFalse();
        assertThat(fareSplits.findByPartyId(exactlyDay).orElseThrow().expire(SWEEP_AT)).isTrue();
    }

    @DisplayName("findOpenByPayer - 그 사람이 받는 사람인 OPEN 정산만 준다(닫힌 정산·보내는 사람인 정산 제외)")
    @Test
    void findOpenByPayer() {

        /*
         * settings
         */
        RideRoster open = finishedParty(PAYER_ID, FIRST_ID);
        RideRoster closed = finishedParty(PAYER_ID, FIRST_ID);
        RideRoster asSender = finishedParty(FIRST_ID, PAYER_ID);
        fareSplits.create(FareSplit.create(open, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(FIRST_ID, 5_000)), Instant.now()));
        fareSplits.create(FareSplit.create(closed, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(FIRST_ID, 5_000)), Instant.now()));
        fareSplits.create(FareSplit.create(asSender, FIRST_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(FIRST_ID, 5_000), pay(PAYER_ID, 5_000)), Instant.now()));
        execute("UPDATE fare_split SET status = 'COMPLETED', account_number = NULL WHERE match_id = ?1", closed.partyId());
        clearContext();

        /*
         * test
         */
        List<Long> found = fareSplits.findOpenByPayer(PAYER_ID);

        /*
         * verify
         */
        assertThat(found).containsExactly(open.partyId());
        assertThat(fareSplits.findOpenByPayer(SECOND_ID)).isEmpty();
    }

    @DisplayName("findShares - 받는 사람과 보내는 사람 모두에게 정산 한 건당 한 줄로 택시비·자기 줄의 부담 금액(사람마다 다르다)을 주고, 정산에 없는 사람에게는 주지 않는다")
    @Test
    void findShares() {

        /*
         * settings
         */
        RideRoster party = finishedPartyHostedBy(PAYER_ID, FIRST_ID, PAYER_ID, SECOND_ID);
        fareSplits.create(FareSplit.create(party, PAYER_ID, 18_700, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 6_700), pay(FIRST_ID, 7_000), pay(SECOND_ID, 5_000)), Instant.now()));
        clearContext();

        /*
         * test
         */
        List<FareShare> payerView = fareSplits.findShares(PAYER_ID, List.of(party.partyId()));
        List<FareShare> senderView = fareSplits.findShares(SECOND_ID, List.of(party.partyId()));
        List<FareShare> otherSenderView = fareSplits.findShares(FIRST_ID, List.of(party.partyId()));
        List<FareShare> outsiderView = fareSplits.findShares(OUTSIDER_ID, List.of(party.partyId()));

        /*
         * verify
         */
        assertThat(payerView).as("받는 사람도 자기 줄의 금액").containsExactly(new FareShare(party.partyId(), 18_700, 6_700));
        assertThat(senderView).as("가장 큰 금액(7,000원)이 아니라 내 금액").containsExactly(new FareShare(party.partyId(), 18_700, 5_000));
        assertThat(otherSenderView).containsExactly(new FareShare(party.partyId(), 18_700, 7_000));
        assertThat(outsiderView).isEmpty();
    }

    @DisplayName("findShares - 넘긴 방 중 정산이 있는 방만 주고, 닫힌 정산(계좌번호 NULL)도 준다")
    @Test
    void findShares_onlyRequestedAndAnyStatus() {

        /*
         * settings
         */
        RideRoster open = finishedParty(PAYER_ID, FIRST_ID);
        RideRoster completed = finishedParty(PAYER_ID, FIRST_ID);
        RideRoster expired = finishedParty(FIRST_ID, PAYER_ID);
        RideRoster notRequested = finishedParty(PAYER_ID, FIRST_ID);
        RideRoster noSplit = finishedParty(PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.create(open, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 6_000), pay(FIRST_ID, 4_000)), Instant.now()));
        fareSplits.create(FareSplit.create(completed, PAYER_ID, 10_001, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 4_991), pay(FIRST_ID, 5_010)), Instant.now()));
        fareSplits.create(FareSplit.create(expired, FIRST_ID, 12_000, Bank.KB, ACCOUNT, List.of(pay(FIRST_ID, 7_500), pay(PAYER_ID, 4_500)), Instant.now()));
        fareSplits.create(FareSplit.create(notRequested, PAYER_ID, 10_000, Bank.KB, ACCOUNT, List.of(pay(PAYER_ID, 5_000), pay(FIRST_ID, 5_000)), Instant.now()));
        execute("UPDATE fare_split SET status = 'COMPLETED', account_number = NULL, closed_at = now() WHERE match_id = ?1", completed.partyId());
        execute("UPDATE fare_split SET status = 'EXPIRED', account_number = NULL, closed_at = now() WHERE match_id = ?1", expired.partyId());
        clearContext();

        /*
         * test
         */
        List<FareShare> found = fareSplits.findShares(PAYER_ID, List.of(open.partyId(), completed.partyId(), expired.partyId(), noSplit.partyId()));

        /*
         * verify
         */
        assertThat(found).containsExactlyInAnyOrder(
                new FareShare(open.partyId(), 10_000, 6_000),
                new FareShare(completed.partyId(), 10_001, 4_991),
                new FareShare(expired.partyId(), 12_000, 4_500));
    }

    @DisplayName("findShares - 넘긴 방이 없으면 빈 목록이다")
    @Test
    void findShares_empty() {

        /*
         * test
         */
        List<FareShare> found = fareSplits.findShares(PAYER_ID, List.of());

        /*
         * verify
         */
        assertThat(found).isEmpty();
    }

    // ───────────────────────── fixtures ─────────────────────────

    /** 넘긴 순서대로 합류하고 모두 도착해 탄 끝난 방. 가장 먼저 합류한 사람이 방장이다 */
    private RideRoster finishedParty(Long... memberIdsInJoinOrder) {
        return finishedPartyHostedBy(memberIdsInJoinOrder[0], memberIdsInJoinOrder);
    }

    /** 넘긴 순서대로 합류하고 모두 도착해 탄 끝난 방. 방장은 hostId 다(가장 먼저 합류한 사람이 아닐 수 있다) */
    private RideRoster finishedPartyHostedBy(Long hostId, Long... memberIdsInJoinOrder) {
        List<Long> members = List.of(memberIdsInJoinOrder);

        return new RideRoster(nextPartyId++, hostId, true, members, members);
    }

    private static FarePayment pay(Long memberId, int amount) {
        return new FarePayment(memberId, amount);
    }

    private String rawAccountNumber(Long partyId) {
        return (String) em.getEntityManager()
                .createNativeQuery("SELECT account_number FROM fare_split WHERE match_id = :id")
                .setParameter("id", partyId)
                .getSingleResult();
    }

    /** 송금 행마다 [payment, request_count], userId 순 - 받는 사람의 줄도 있다 */
    private List<List<Integer>> rawPaymentsAndRequestCounts(Long partyId) {
        List<?> rows = em.getEntityManager()
                .createNativeQuery("SELECT payment, request_count FROM fare_split_transfer WHERE match_id = :id ORDER BY user_id")
                .setParameter("id", partyId)
                .getResultList();

        return rows.stream()
                .map(row -> (Object[]) row)
                .map(row -> List.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()))
                .toList();
    }

    /** 이미 낸 사람의 줄(payment = 부담금, sent_at = confirmed_at = 시작 시각, request_count = 0)로 들어간 행 수 */
    private long rawPaidRowCount(Long partyId, Long memberId, int payment, Instant startedAt) {
        Object count = em.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM fare_split_transfer WHERE match_id = :id AND user_id = :userId AND payment = :payment "
                        + "AND sent_at = :startedAt AND confirmed_at = :startedAt AND request_count = 0")
                .setParameter("id", partyId)
                .setParameter("userId", memberId)
                .setParameter("payment", payment)
                .setParameter("startedAt", startedAt)
                .getSingleResult();

        return ((Number) count).longValue();
    }

    private void setCreatedAt(Long partyId, Instant createdAt) {
        em.getEntityManager().createNativeQuery("UPDATE fare_split SET created_at = :createdAt WHERE match_id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", partyId)
                .executeUpdate();
    }

    /** 받는 사람·보내는 사람 둘이 탄 방에 createdAt 에 시작한 정산(택시비 10,000원, 보내는 사람마다 3,000원, 나머지는 받는 사람 부담). 첫 송금 요청만 보냈다(요청 횟수 1) */
    private Long splitCreatedAt(Instant createdAt) {
        return splitCreatedAt(createdAt, PAYER_ID, FIRST_ID);
    }

    /** 받는 사람과 보내는 사람 둘(FIRST_ID·SECOND_ID)이 탄 방에 createdAt 에 시작한 정산 */
    private Long threeRiderSplitCreatedAt(Instant createdAt) {
        return splitCreatedAt(createdAt, PAYER_ID, FIRST_ID, SECOND_ID);
    }

    private Long splitCreatedAt(Instant createdAt, Long... memberIdsInJoinOrder) {
        RideRoster party = finishedParty(memberIdsInJoinOrder);
        Long payerId = memberIdsInJoinOrder[0];
        int payerPayment = 10_000 - 3_000 * (memberIdsInJoinOrder.length - 1);
        List<FarePayment> payments = party.riderIds().stream()
                .map(riderId -> pay(riderId, riderId.equals(payerId) ? payerPayment : 3_000))
                .toList();
        fareSplits.create(FareSplit.create(party, payerId, 10_000, Bank.KB, ACCOUNT, payments, createdAt));
        clearContext();
        setCreatedAt(party.partyId(), createdAt);
        clearContext();

        return party.partyId();
    }

    /** splitCreatedAt 과 같되 보내는 사람에게 송금 요청을 requestCount 번 보낸 정산(받는 사람의 줄은 0 그대로) */
    private Long splitRequested(int requestCount, Instant createdAt) {
        Long partyId = splitCreatedAt(createdAt);
        execute("UPDATE fare_split_transfer SET request_count = ?1 WHERE match_id = ?2 AND user_id <> ?3", requestCount, partyId, PAYER_ID);

        return partyId;
    }

    private static Instant hoursBeforeSweep(int hours) {
        return SWEEP_AT.minus(Duration.ofHours(hours));
    }

    private void execute(String sql, Object... params) {
        var query = em.getEntityManager().createNativeQuery(sql);
        for(int i = 0; i < params.length; i++) query.setParameter(i + 1, params[i]);
        query.executeUpdate();
    }

    private void clearContext() {
        em.flush();
        em.clear();
    }

    // 실패한 INSERT 가 남은 영속성 컨텍스트는 flush 하지 않고 버린다
    private void clearContextQuietly() {
        em.clear();
    }
}
