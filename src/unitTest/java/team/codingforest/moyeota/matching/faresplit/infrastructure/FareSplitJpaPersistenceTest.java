package team.codingforest.moyeota.matching.faresplit.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartyRiders;
import team.codingforest.moyeota.matching.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.matching.faresplit.domain.Bank;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplit;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.matching.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.matching.faresplit.domain.FareShare;
import team.codingforest.moyeota.matching.faresplit.domain.RideRoster;
import team.codingforest.moyeota.matching.faresplit.domain.TransferStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 *  H2 에서 엔티티 매핑(암호화 컨버터, 복합키 송금 행)과 합류 순 정렬, 변경 저장·삭제, 내가 보낼 정산 목록 쿼리,
 *  스케줄러 대상 조회(재요청·입금 확인 요청·만료)와 받는 사람별 OPEN 정산 조회, 탑승 기록용 정산 금액 조회를 본다.
 *  합류 순은 matching 창구(PartyAccess)가 알려 준 대로 담는지만 본다 - 실제 합류 순은 PartyAccessPostgresTest 가 본다.
 *  마이그레이션·제약·행 잠금은 FareSplitPostgresTest·FareSplitSweepPostgresTest 가 본다
 */
@DataJpaTest
@Import({FareSplitJpa.class, JpaAuditingConfig.class})
class FareSplitJpaPersistenceTest {
    private static final Long PAYER_ID = 11L;
    private static final Long FIRST_ID = 12L;
    private static final Long SECOND_ID = 13L;
    private static final Long OUTSIDER_ID = 99L;
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");
    private static final AccountNumber NEW_ACCOUNT = new AccountNumber("110123456789");
    // 한국 시각 11-05 12:00
    private static final Instant SWEEP_AT = Instant.parse("2026-11-05T03:00:00Z");

    private final FareSplitJpa fareSplits;
    private final TestEntityManager em;
    private long nextPartyId = 1_000L;

    @MockitoBean
    private PartyAccess partyAccess;

    @Autowired
    FareSplitJpaPersistenceTest(FareSplitJpa fareSplits, TestEntityManager em) {
        this.fareSplits = fareSplits;
        this.em = em;
    }

    @DisplayName("create - 정산과 송금 행을 저장하고 다시 읽으면 같은 값이 돌아온다")
    @Test
    void create() {

        /*
         * settings
         */
        RideRoster party = finishedParty(SECOND_ID, PAYER_ID, FIRST_ID);
        FareSplit split = FareSplit.open(party, PAYER_ID, 18_700, Bank.SHINHAN, ACCOUNT, Instant.now());

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
        assertThat(found.getAmountPerPerson()).isEqualTo(6_240);
        assertThat(found.getBank()).isEqualTo(Bank.SHINHAN);
        assertThat(found.getAccountNumber()).isEqualTo(ACCOUNT);
        assertThat(found.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(found.getClosedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getTransfers()).allSatisfy(t -> {
            assertThat(t.status()).isEqualTo(TransferStatus.NOT_SENT);
            assertThat(t.getRequestCount()).as("시작할 때 보낸 첫 송금 요청").isEqualTo(1);
            assertThat(t.getLastRequestedAt()).isNotNull();
        });
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
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
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

    @DisplayName("findByPartyId - 송금은 userId 순이 아니라 방 합류 순으로 담는다")
    @Test
    void findByPartyId_joinOrder() {

        /*
         * settings
         */
        RideRoster party = finishedParty(SECOND_ID, PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();

        /*
         * test
         */
        FareSplit found = fareSplits.findByPartyId(party.partyId()).orElseThrow();

        /*
         * verify
         */
        assertThat(found.getTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(SECOND_ID, FIRST_ID);
    }

    @DisplayName("findByPartyId / existsByPartyId - 정산이 없는 방이면 empty / false")
    @Test
    void findByPartyId_none() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID);

        /*
         * test
         */
        boolean exists = fareSplits.existsByPartyId(party.partyId());

        /*
         * verify
         */
        assertThat(exists).isFalse();
        assertThat(fareSplits.findByPartyId(party.partyId())).isEmpty();
    }

    @DisplayName("create - 같은 방에 두 번 만들면 덮어쓰지 않고 DataIntegrityViolationException")
    @Test
    void create_duplicate() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> fareSplits.create(FareSplit.open(party, FIRST_ID, 20_000, Bank.KB, ACCOUNT, Instant.now())));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOfAny(DataIntegrityViolationException.class, org.hibernate.exception.ConstraintViolationException.class);
        clearContextQuietly();
        assertThat(fareSplits.findByPartyId(party.partyId()).orElseThrow().getPayerId()).as("처음 만든 정산이 남는다").isEqualTo(PAYER_ID);
    }

    @DisplayName("existsByPartyId - 만든 정산이 있으면 true")
    @Test
    void existsByPartyId() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();

        /*
         * test
         */
        boolean exists = fareSplits.existsByPartyId(party.partyId());

        /*
         * verify
         */
        assertThat(exists).isTrue();
    }


    // ───────────────────────── findByPartyIdForUpdate ─────────────────────────

    @DisplayName("findByPartyIdForUpdate - 잠가 읽어도 송금을 방 합류 순으로 담고, 정산이 없으면 empty")
    @Test
    void findByPartyIdForUpdate() {

        /*
         * settings
         */
        RideRoster party = finishedParty(SECOND_ID, PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();

        /*
         * test
         */
        FareSplit found = fareSplits.findByPartyIdForUpdate(party.partyId()).orElseThrow();

        /*
         * verify
         */
        assertThat(found.getTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(SECOND_ID, FIRST_ID);
        assertThat(found.getAccountNumber()).isEqualTo(ACCOUNT);
        assertThat(fareSplits.findByPartyIdForUpdate(party.partyId() + 1_000)).isEmpty();
    }

    // ───────────────────────── save ─────────────────────────

    @DisplayName("save - 송금 시각을 옮기고, 모두 확인돼 닫히면 상태·닫힌 시각과 계좌번호 삭제(NULL)를 옮긴다")
    @Test
    void save_transfersAndCompletion() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, Instant.now()));
        clearContext();
        Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant confirmedAt = sentAt.plusSeconds(60);

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
        assertThat(rawAccountNumber(party.partyId())).isNull();
    }

    @DisplayName("save - 못 받음으로 되돌리면 보낸 시각이 지워진다")
    @Test
    void save_notReceived() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
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
    }

    @DisplayName("save - 고친 택시비·1인당 금액·은행·계좌번호를 옮기고 계좌번호는 다시 암호문으로 저장된다")
    @Test
    void save_edit() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, Instant.now()));
        clearContext();

        /*
         * test
         */
        FareSplit split = fareSplits.findByPartyIdForUpdate(party.partyId()).orElseThrow();
        split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT);
        fareSplits.save(split);
        clearContext();

        /*
         * verify
         */
        FareSplit found = fareSplits.findByPartyId(party.partyId()).orElseThrow();
        assertThat(found.getTotalFare()).isEqualTo(20_000);
        assertThat(found.getAmountPerPerson()).isEqualTo(6_670);
        assertThat(found.getBank()).isEqualTo(Bank.SHINHAN);
        assertThat(found.getAccountNumber()).isEqualTo(NEW_ACCOUNT);
        assertThat(found.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(rawAccountNumber(party.partyId())).startsWith("v1:").doesNotContain(NEW_ACCOUNT.value());
    }

    // ───────────────────────── delete ─────────────────────────

    @DisplayName("delete - 정산과 송금 행을 모두 지우고, 같은 방에 다시 만들 수 있다")
    @Test
    void delete() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, Instant.now()));
        clearContext();
        fareSplits.findByPartyIdForUpdate(party.partyId()).orElseThrow();

        /*
         * test
         */
        fareSplits.delete(party.partyId());
        clearContext();

        /*
         * verify
         */
        assertThat(fareSplits.existsByPartyId(party.partyId())).isFalse();
        assertThat(count("SELECT count(*) FROM fare_split_transfer WHERE match_id = :id", party.partyId())).isZero();
        fareSplits.create(FareSplit.open(party, FIRST_ID, 12_000, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();
        assertThat(fareSplits.findByPartyId(party.partyId()).orElseThrow().getPayerId()).isEqualTo(FIRST_ID);
    }

    // ───────────────────────── findOpenTransfers ─────────────────────────

    @DisplayName("findOpenTransfers - 내가 보내는 사람인 OPEN 정산만 받는 사람·내 송금 시각과 함께 시작 최신순으로 준다")
    @Test
    void findOpenTransfers() {

        /*
         * settings
         */
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
        RideRoster older = finishedParty(PAYER_ID, FIRST_ID);
        RideRoster newer = finishedParty(SECOND_ID, FIRST_ID);
        RideRoster completed = finishedParty(PAYER_ID, FIRST_ID);
        RideRoster mine = finishedParty(FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.open(older, PAYER_ID, 10_000, Bank.KB, ACCOUNT, base));
        fareSplits.create(FareSplit.open(newer, SECOND_ID, 12_000, Bank.KB, ACCOUNT, base));
        fareSplits.create(FareSplit.open(completed, PAYER_ID, 10_000, Bank.KB, ACCOUNT, base));
        fareSplits.create(FareSplit.open(mine, FIRST_ID, 10_000, Bank.KB, ACCOUNT, base));
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
        assertThat(transfers).extracting(OpenTransfer::partyId).containsExactly(newer.partyId(), older.partyId());
        OpenTransfer first = transfers.get(0);
        assertThat(first.payerId()).isEqualTo(SECOND_ID);
        assertThat(first.amountPerPerson()).isEqualTo(6_000);
        assertThat(first.sentAt()).isEqualTo(base);
        assertThat(first.confirmedAt()).isNull();
        assertThat(first.status()).isEqualTo(TransferStatus.SENT);
        assertThat(first.createdAt()).isEqualTo(base.minus(Duration.ofHours(1)));
        assertThat(transfers.get(1).status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(transfers.get(1).payerId()).isEqualTo(PAYER_ID);
        assertThat(fareSplits.findOpenTransfers(PAYER_ID)).as("받는 사람으로만 있는 정산은 나오지 않는다").isEmpty();
    }

    // ───────────────────────── 알림 차례 저장 ─────────────────────────

    @DisplayName("save - 송금 재요청 차례·마지막 요청 시각과 입금 확인 요청 시각을 옮긴다")
    @Test
    void save_requestTurns() {

        /*
         * settings
         */
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, Instant.now()));
        clearContext();
        // 한국 시각 11-03 21:40 에 시작했다
        Instant createdAt = Instant.parse("2026-11-03T12:40:00Z");
        Instant sentAt = createdAt.plus(Duration.ofMinutes(5));
        setCreatedAt(party.partyId(), createdAt);
        clearContext();
        Instant remindAt = createdAt.plus(Duration.ofHours(1));
        Instant confirmAt = sentAt.plus(Duration.ofHours(24));

        /*
         * test
         */
        FareSplit split = fareSplits.findByPartyIdForUpdate(party.partyId()).orElseThrow();
        split.markSent(SECOND_ID, sentAt);
        split.remindTransfers(remindAt);
        split.requestConfirm(confirmAt);
        fareSplits.save(split);
        clearContext();

        /*
         * verify
         */
        FareSplit found = fareSplits.findByPartyId(party.partyId()).orElseThrow();
        FareSplitTransfer first = found.findTransfer(FIRST_ID).orElseThrow();
        assertThat(first.getRequestCount()).isEqualTo(2);
        assertThat(first.getLastRequestedAt()).isEqualTo(remindAt);
        assertThat(first.getConfirmRequestedAt()).isNull();
        FareSplitTransfer second = found.findTransfer(SECOND_ID).orElseThrow();
        assertThat(second.getRequestCount()).isEqualTo(2);
        assertThat(second.getConfirmRequestedAt()).isEqualTo(confirmAt);
    }

    // ───────────────────────── 스케줄러 대상 조회 ─────────────────────────

    @DisplayName("findTransferRequestTargets - 재요청 차례(1시간 뒤, 다음 날·그다음 날 10시 한국 시각)가 지난 확인 안 된 송금이 있는 OPEN·계좌 있는 정산만 주고, 도메인 판단과 같다")
    @Test
    void findTransferRequestTargets() {

        /*
         * settings
         */
        Long dueSecond = splitCreatedAt(SWEEP_AT.minus(Duration.ofHours(2)), 1);
        Long notYetSecond = splitCreatedAt(SWEEP_AT.minus(Duration.ofMinutes(30)), 1);
        Long dueThird = splitCreatedAt(Instant.parse("2026-11-04T00:00:00Z"), 2);        // 11-04 09:00 KST → 11-05 10:00
        Long notYetThird = splitCreatedAt(Instant.parse("2026-11-04T15:30:00Z"), 2);     // 11-05 00:30 KST → 11-06 10:00
        Long dueFourth = splitCreatedAt(Instant.parse("2026-11-03T11:00:00Z"), 3);       // 11-03 20:00 KST → 11-05 10:00
        Long exhausted = splitCreatedAt(Instant.parse("2026-11-01T03:00:00Z"), 4);
        Long confirmedOnly = splitCreatedAt(SWEEP_AT.minus(Duration.ofHours(2)), 1);
        execute("UPDATE fare_split_transfer SET confirmed_at = ?1 WHERE match_id = ?2", SWEEP_AT, confirmedOnly);
        Long expired = splitCreatedAt(SWEEP_AT.minus(Duration.ofHours(2)), 1);
        execute("UPDATE fare_split SET status = 'EXPIRED', account_number = NULL WHERE match_id = ?1", expired);
        Long noAccount = splitCreatedAt(SWEEP_AT.minus(Duration.ofHours(2)), 1);
        execute("UPDATE fare_split SET account_number = NULL WHERE match_id = ?1", noAccount);
        clearContext();

        /*
         * test
         */
        List<Long> targets = fareSplits.findTransferRequestTargets(SWEEP_AT);

        /*
         * verify
         */
        assertThat(targets).containsExactlyInAnyOrder(dueSecond, dueThird, dueFourth);
        for(Long partyId : List.of(dueSecond, notYetSecond, dueThird, notYetThird, dueFourth, exhausted, confirmedOnly, expired, noAccount)) {
            boolean due = fareSplits.findByPartyId(partyId).orElseThrow().remindTransfers(SWEEP_AT).handled();
            assertThat(due).as("partyId=%d 의 쿼리 결과와 도메인 판단", partyId).isEqualTo(targets.contains(partyId));
        }
    }

    @DisplayName("findConfirmRequestTargets - 보냈다고 누른 지 24시간이 지나도(경계 포함) 확인·요청되지 않은 송금이 있는 OPEN 정산만 준다")
    @Test
    void findConfirmRequestTargets() {

        /*
         * settings
         */
        Instant dayAgo = SWEEP_AT.minus(Duration.ofHours(24));
        Long due = splitCreatedAt(SWEEP_AT.minus(Duration.ofDays(2)), 2);
        execute("UPDATE fare_split_transfer SET sent_at = ?1 WHERE match_id = ?2", dayAgo, due);
        Long notYet = splitCreatedAt(SWEEP_AT.minus(Duration.ofDays(2)), 2);
        execute("UPDATE fare_split_transfer SET sent_at = ?1 WHERE match_id = ?2", dayAgo.plusSeconds(1), notYet);
        Long requested = splitCreatedAt(SWEEP_AT.minus(Duration.ofDays(2)), 2);
        execute("UPDATE fare_split_transfer SET sent_at = ?1, confirm_requested_at = ?2 WHERE match_id = ?3", dayAgo.minusSeconds(3600), dayAgo, requested);
        Long confirmed = splitCreatedAt(SWEEP_AT.minus(Duration.ofDays(2)), 2);
        execute("UPDATE fare_split_transfer SET sent_at = ?1, confirmed_at = ?2 WHERE match_id = ?3", dayAgo.minusSeconds(3600), dayAgo, confirmed);
        Long notSent = splitCreatedAt(SWEEP_AT.minus(Duration.ofDays(2)), 2);
        Long expired = splitCreatedAt(SWEEP_AT.minus(Duration.ofDays(2)), 2);
        execute("UPDATE fare_split_transfer SET sent_at = ?1 WHERE match_id = ?2", dayAgo.minusSeconds(3600), expired);
        execute("UPDATE fare_split SET status = 'EXPIRED', account_number = NULL WHERE match_id = ?1", expired);
        clearContext();

        /*
         * test
         */
        List<Long> targets = fareSplits.findConfirmRequestTargets(SWEEP_AT);

        /*
         * verify
         */
        assertThat(targets).containsExactly(due);
        for(Long partyId : List.of(due, notYet, requested, confirmed, notSent, expired)) {
            boolean isDue = fareSplits.findByPartyId(partyId).orElseThrow().requestConfirm(SWEEP_AT).handled();
            assertThat(isDue).as("partyId=%d 의 쿼리 결과와 도메인 판단", partyId).isEqualTo(targets.contains(partyId));
        }
    }

    @DisplayName("findExpireTargets - 시작하고 7일이 지난(경계 포함) OPEN 정산만 오래된 순으로 준다")
    @Test
    void findExpireTargets() {

        /*
         * settings
         */
        Instant weekAgo = SWEEP_AT.minus(Duration.ofDays(7));
        Long older = splitCreatedAt(weekAgo.minus(Duration.ofDays(1)), 4);
        Long exactlyWeek = splitCreatedAt(weekAgo, 4);
        Long notYet = splitCreatedAt(weekAgo.plusSeconds(1), 4);
        Long completed = splitCreatedAt(weekAgo.minus(Duration.ofDays(1)), 4);
        execute("UPDATE fare_split SET status = 'COMPLETED', account_number = NULL WHERE match_id = ?1", completed);
        clearContext();

        /*
         * test
         */
        List<Long> targets = fareSplits.findExpireTargets(SWEEP_AT);

        /*
         * verify
         */
        assertThat(targets).containsExactly(older, exactlyWeek);
        assertThat(fareSplits.findByPartyId(notYet).orElseThrow().expire(SWEEP_AT)).isFalse();
        assertThat(fareSplits.findByPartyId(exactlyWeek).orElseThrow().expire(SWEEP_AT)).isTrue();
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
        fareSplits.create(FareSplit.open(open, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        fareSplits.create(FareSplit.open(closed, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        fareSplits.create(FareSplit.open(asSender, FIRST_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
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

    @DisplayName("findShares - 받는 사람과 보내는 사람 모두에게 정산 한 건당 한 줄로 택시비·1인당 금액·보내는 사람 수를 주고, 정산에 없는 사람에게는 주지 않는다")
    @Test
    void findShares() {

        /*
         * settings
         */
        RideRoster party = finishedParty(FIRST_ID, PAYER_ID, SECOND_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 18_700, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();

        /*
         * test
         */
        List<FareShare> payerView = fareSplits.findShares(PAYER_ID, List.of(party.partyId()));
        List<FareShare> senderView = fareSplits.findShares(SECOND_ID, List.of(party.partyId()));
        List<FareShare> outsiderView = fareSplits.findShares(OUTSIDER_ID, List.of(party.partyId()));

        /*
         * verify
         */
        FareShare expected = new FareShare(party.partyId(), PAYER_ID, 18_700, 6_240, 2);
        assertThat(payerView).containsExactly(expected);
        assertThat(senderView).containsExactly(expected);
        assertThat(outsiderView).isEmpty();
        assertThat(payerView.getFirst().amountOf(PAYER_ID)).isEqualTo(6_220);
        assertThat(senderView.getFirst().amountOf(SECOND_ID)).isEqualTo(6_240);
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
        fareSplits.create(FareSplit.open(open, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        fareSplits.create(FareSplit.open(completed, PAYER_ID, 10_001, Bank.KB, ACCOUNT, Instant.now()));
        fareSplits.create(FareSplit.open(expired, FIRST_ID, 12_000, Bank.KB, ACCOUNT, Instant.now()));
        fareSplits.create(FareSplit.open(notRequested, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
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
                new FareShare(open.partyId(), PAYER_ID, 10_000, 5_000, 1),
                new FareShare(completed.partyId(), PAYER_ID, 10_001, 5_010, 1),
                new FareShare(expired.partyId(), FIRST_ID, 12_000, 6_000, 1));
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

    /** 넘긴 순서대로 합류하고 모두 도착해 탄 끝난 방. 합류 순은 matching 창구가 알려 준다 */
    private RideRoster finishedParty(Long... memberIdsInJoinOrder) {
        Long partyId = nextPartyId++;
        List<Long> members = List.of(memberIdsInJoinOrder);
        doReturn(Optional.of(new PartyRiders(partyId, true, members, members))).when(partyAccess).findRiders(eq(partyId));

        return new RideRoster(partyId, true, members, members);
    }

    private String rawAccountNumber(Long partyId) {
        return (String) em.getEntityManager()
                .createNativeQuery("SELECT account_number FROM fare_split WHERE match_id = :id")
                .setParameter("id", partyId)
                .getSingleResult();
    }

    private long count(String sql, Long partyId) {
        return ((Number) em.getEntityManager().createNativeQuery(sql).setParameter("id", partyId).getSingleResult()).longValue();
    }

    private void setCreatedAt(Long partyId, Instant createdAt) {
        em.getEntityManager().createNativeQuery("UPDATE fare_split SET created_at = :createdAt WHERE match_id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", partyId)
                .executeUpdate();
    }

    /** 받는 사람·보내는 사람 둘이 탄 방에 createdAt 에 시작한 정산. 보내는 사람의 송금 요청 차례는 requestCount 번째까지 처리했다 */
    private Long splitCreatedAt(Instant createdAt, int requestCount) {
        RideRoster party = finishedParty(PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, createdAt));
        clearContext();
        setCreatedAt(party.partyId(), createdAt);
        execute("UPDATE fare_split_transfer SET request_count = ?1 WHERE match_id = ?2", requestCount, party.partyId());
        clearContext();

        return party.partyId();
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
