package team.codingforest.moyeota.matching.faresplit.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.matching.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.matching.faresplit.domain.Bank;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplit;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.matching.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.matching.faresplit.domain.TransferStatus;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;
import team.codingforest.moyeota.matching.party.infrastructure.PartyJpa;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  H2 에서 엔티티 매핑(암호화 컨버터, 복합키 송금 행)과 합류 순 정렬, 변경 저장·삭제, 내가 보낼 정산 목록 쿼리를 본다.
 *  마이그레이션·제약·행 잠금은 FareSplitPostgresTest 가 본다
 */
@DataJpaTest
@Import({FareSplitJpa.class, PartyJpa.class, JpaAuditingConfig.class})
class FareSplitJpaPersistenceTest {
    private static final Long PAYER_ID = 11L;
    private static final Long FIRST_ID = 12L;
    private static final Long SECOND_ID = 13L;
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");
    private static final AccountNumber NEW_ACCOUNT = new AccountNumber("110123456789");

    private final FareSplitJpa fareSplits;
    private final PartyJpa parties;
    private final TestEntityManager em;

    @Autowired
    FareSplitJpaPersistenceTest(FareSplitJpa fareSplits, PartyJpa parties, TestEntityManager em) {
        this.fareSplits = fareSplits;
        this.parties = parties;
        this.em = em;
    }

    @DisplayName("create - 정산과 송금 행을 저장하고 다시 읽으면 같은 값이 돌아온다")
    @Test
    void create() {

        /*
         * settings
         */
        Party party = finishedParty(SECOND_ID, PAYER_ID, FIRST_ID);
        FareSplit split = FareSplit.open(party, PAYER_ID, 18_700, Bank.SHINHAN, ACCOUNT, Instant.now());

        /*
         * test
         */
        FareSplit created = fareSplits.create(split);
        clearContext();
        FareSplit found = fareSplits.findByPartyId(party.getId()).orElseThrow();

        /*
         * verify
         */
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(found.getPartyId()).isEqualTo(party.getId());
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
            assertThat(t.getRequestCount()).isZero();
        });
    }

    @DisplayName("create - 계좌번호는 암호문으로 저장된다")
    @Test
    void create_encryptsAccountNumber() {

        /*
         * settings
         */
        Party party = finishedParty(PAYER_ID, FIRST_ID);

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
                .setParameter("id", party.getId())
                .getSingleResult();
        assertThat(stored).startsWith("v1:").doesNotContain(ACCOUNT.value());
    }

    @DisplayName("findByPartyId - 송금은 userId 순이 아니라 방 합류 순으로 담는다")
    @Test
    void findByPartyId_joinOrder() {

        /*
         * settings
         */
        Party party = finishedParty(SECOND_ID, PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();

        /*
         * test
         */
        FareSplit found = fareSplits.findByPartyId(party.getId()).orElseThrow();

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
        Party party = finishedParty(PAYER_ID, FIRST_ID);

        /*
         * test
         */
        boolean exists = fareSplits.existsByPartyId(party.getId());

        /*
         * verify
         */
        assertThat(exists).isFalse();
        assertThat(fareSplits.findByPartyId(party.getId())).isEmpty();
    }

    @DisplayName("create - 같은 방에 두 번 만들면 덮어쓰지 않고 DataIntegrityViolationException")
    @Test
    void create_duplicate() {

        /*
         * settings
         */
        Party party = finishedParty(PAYER_ID, FIRST_ID);
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
        assertThat(fareSplits.findByPartyId(party.getId()).orElseThrow().getPayerId()).as("처음 만든 정산이 남는다").isEqualTo(PAYER_ID);
    }

    @DisplayName("existsByPartyId - 만든 정산이 있으면 true")
    @Test
    void existsByPartyId() {

        /*
         * settings
         */
        Party party = finishedParty(PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();

        /*
         * test
         */
        boolean exists = fareSplits.existsByPartyId(party.getId());

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
        Party party = finishedParty(SECOND_ID, PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();

        /*
         * test
         */
        FareSplit found = fareSplits.findByPartyIdForUpdate(party.getId()).orElseThrow();

        /*
         * verify
         */
        assertThat(found.getTransfers()).extracting(FareSplitTransfer::getMemberId).containsExactly(SECOND_ID, FIRST_ID);
        assertThat(found.getAccountNumber()).isEqualTo(ACCOUNT);
        assertThat(fareSplits.findByPartyIdForUpdate(party.getId() + 1_000)).isEmpty();
    }

    // ───────────────────────── save ─────────────────────────

    @DisplayName("save - 송금 시각을 옮기고, 모두 확인돼 닫히면 상태·닫힌 시각과 계좌번호 삭제(NULL)를 옮긴다")
    @Test
    void save_transfersAndCompletion() {

        /*
         * settings
         */
        Party party = finishedParty(PAYER_ID, FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, Instant.now()));
        clearContext();
        Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant confirmedAt = sentAt.plusSeconds(60);

        /*
         * test
         */
        FareSplit split = fareSplits.findByPartyIdForUpdate(party.getId()).orElseThrow();
        split.markSent(FIRST_ID, sentAt);
        split.confirmReceived(PAYER_ID, FIRST_ID, confirmedAt);
        split.confirmReceived(PAYER_ID, SECOND_ID, confirmedAt);
        fareSplits.save(split);
        clearContext();

        /*
         * verify
         */
        FareSplit found = fareSplits.findByPartyId(party.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(FareSplitStatus.COMPLETED);
        assertThat(found.getClosedAt()).isEqualTo(confirmedAt);
        assertThat(found.getAccountNumber()).isNull();
        FareSplitTransfer first = found.findTransfer(FIRST_ID).orElseThrow();
        assertThat(first.getSentAt()).isEqualTo(sentAt);
        assertThat(first.getConfirmedAt()).isEqualTo(confirmedAt);
        FareSplitTransfer second = found.findTransfer(SECOND_ID).orElseThrow();
        assertThat(second.getSentAt()).isNull();
        assertThat(second.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(rawAccountNumber(party.getId())).isNull();
    }

    @DisplayName("save - 못 받음으로 되돌리면 보낸 시각이 지워진다")
    @Test
    void save_notReceived() {

        /*
         * settings
         */
        Party party = finishedParty(PAYER_ID, FIRST_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 10_000, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();
        FareSplit sent = fareSplits.findByPartyIdForUpdate(party.getId()).orElseThrow();
        sent.markSent(FIRST_ID, Instant.now());
        fareSplits.save(sent);
        clearContext();

        /*
         * test
         */
        FareSplit split = fareSplits.findByPartyIdForUpdate(party.getId()).orElseThrow();
        split.markNotReceived(PAYER_ID, FIRST_ID);
        fareSplits.save(split);
        clearContext();

        /*
         * verify
         */
        FareSplitTransfer transfer = fareSplits.findByPartyId(party.getId()).orElseThrow().findTransfer(FIRST_ID).orElseThrow();
        assertThat(transfer.status()).isEqualTo(TransferStatus.NOT_SENT);
        assertThat(transfer.getSentAt()).isNull();
    }

    @DisplayName("save - 고친 택시비·1인당 금액·은행·계좌번호를 옮기고 계좌번호는 다시 암호문으로 저장된다")
    @Test
    void save_edit() {

        /*
         * settings
         */
        Party party = finishedParty(PAYER_ID, FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, Instant.now()));
        clearContext();

        /*
         * test
         */
        FareSplit split = fareSplits.findByPartyIdForUpdate(party.getId()).orElseThrow();
        split.edit(PAYER_ID, 20_000, Bank.SHINHAN, NEW_ACCOUNT);
        fareSplits.save(split);
        clearContext();

        /*
         * verify
         */
        FareSplit found = fareSplits.findByPartyId(party.getId()).orElseThrow();
        assertThat(found.getTotalFare()).isEqualTo(20_000);
        assertThat(found.getAmountPerPerson()).isEqualTo(6_670);
        assertThat(found.getBank()).isEqualTo(Bank.SHINHAN);
        assertThat(found.getAccountNumber()).isEqualTo(NEW_ACCOUNT);
        assertThat(found.getStatus()).isEqualTo(FareSplitStatus.OPEN);
        assertThat(rawAccountNumber(party.getId())).startsWith("v1:").doesNotContain(NEW_ACCOUNT.value());
    }

    // ───────────────────────── delete ─────────────────────────

    @DisplayName("delete - 정산과 송금 행을 모두 지우고, 같은 방에 다시 만들 수 있다")
    @Test
    void delete() {

        /*
         * settings
         */
        Party party = finishedParty(PAYER_ID, FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.open(party, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, Instant.now()));
        clearContext();
        fareSplits.findByPartyIdForUpdate(party.getId()).orElseThrow();

        /*
         * test
         */
        fareSplits.delete(party.getId());
        clearContext();

        /*
         * verify
         */
        assertThat(fareSplits.existsByPartyId(party.getId())).isFalse();
        assertThat(count("SELECT count(*) FROM fare_split_transfer WHERE match_id = :id", party.getId())).isZero();
        fareSplits.create(FareSplit.open(party, FIRST_ID, 12_000, Bank.KB, ACCOUNT, Instant.now()));
        clearContext();
        assertThat(fareSplits.findByPartyId(party.getId()).orElseThrow().getPayerId()).isEqualTo(FIRST_ID);
    }

    // ───────────────────────── findOpenTransfers ─────────────────────────

    @DisplayName("findOpenTransfers - 내가 보내는 사람인 OPEN 정산만 방 이름·받는 사람·내 송금 시각과 함께 시작 최신순으로 준다")
    @Test
    void findOpenTransfers() {

        /*
         * settings
         */
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Party older = finishedParty(PAYER_ID, FIRST_ID);
        Party newer = finishedParty(SECOND_ID, FIRST_ID);
        Party completed = finishedParty(PAYER_ID, FIRST_ID);
        Party mine = finishedParty(FIRST_ID, SECOND_ID);
        fareSplits.create(FareSplit.open(older, PAYER_ID, 10_000, Bank.KB, ACCOUNT, base));
        fareSplits.create(FareSplit.open(newer, SECOND_ID, 12_000, Bank.KB, ACCOUNT, base));
        fareSplits.create(FareSplit.open(completed, PAYER_ID, 10_000, Bank.KB, ACCOUNT, base));
        fareSplits.create(FareSplit.open(mine, FIRST_ID, 10_000, Bank.KB, ACCOUNT, base));
        clearContext();
        setCreatedAt(older.getId(), base.minus(Duration.ofHours(2)));
        setCreatedAt(newer.getId(), base.minus(Duration.ofHours(1)));
        FareSplit sent = fareSplits.findByPartyIdForUpdate(newer.getId()).orElseThrow();
        sent.markSent(FIRST_ID, base);
        fareSplits.save(sent);
        FareSplit done = fareSplits.findByPartyIdForUpdate(completed.getId()).orElseThrow();
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
        assertThat(transfers).extracting(OpenTransfer::partyId).containsExactly(newer.getId(), older.getId());
        OpenTransfer first = transfers.get(0);
        assertThat(first.departure()).isEqualTo("강남역");
        assertThat(first.destination()).isEqualTo("판교역");
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

    // ───────────────────────── fixtures ─────────────────────────

    /** 넘긴 순서대로 합류하고 모두 도착해 탄 방 */
    private Party finishedParty(Long... memberIdsInJoinOrder) {
        Instant meetingAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(Duration.ofHours(1));
        List<PartyMember> members = new java.util.ArrayList<>();
        for(int i = 0; i < memberIdsInJoinOrder.length; i++) {
            members.add(PartyMember.load(memberIdsInJoinOrder[i], meetingAt.minusSeconds(60 - i * 10L), meetingAt, null, null));
        }

        Party saved = parties.save(Party.load(null, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), "강남역", "판교역",
                new Capacity(3), members, memberIdsInJoinOrder[0], meetingAt.minus(Duration.ofHours(1)), PartyStatus.FINISHED,
                12000, 25, "encoded-path", 0L, RoomType.REALTIME, meetingAt, null));
        em.flush();

        // 합류 시각(created_at)은 감사(auditing)가 저장 순간으로 채워 같은 값이 될 수 있다 - 합류 순을 확실히 하려고 직접 넣는다
        for(int i = 0; i < memberIdsInJoinOrder.length; i++) {
            em.getEntityManager().createNativeQuery("UPDATE user_match_room SET created_at = :joinedAt WHERE match_id = :partyId AND user_id = :userId")
                    .setParameter("joinedAt", meetingAt.minusSeconds(60 - i * 10L))
                    .setParameter("partyId", saved.getId())
                    .setParameter("userId", memberIdsInJoinOrder[i])
                    .executeUpdate();
        }
        clearContext();

        return parties.findById(saved.getId()).orElseThrow();
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

    private void clearContext() {
        em.flush();
        em.clear();
    }

    // 실패한 INSERT 가 남은 영속성 컨텍스트는 flush 하지 않고 버린다
    private void clearContextQuietly() {
        em.clear();
    }
}
