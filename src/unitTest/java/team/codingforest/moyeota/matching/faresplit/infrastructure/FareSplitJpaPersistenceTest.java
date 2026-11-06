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
 *  H2 에서 엔티티 매핑(암호화 컨버터, 복합키 송금 행)과 합류 순 정렬을 본다. 마이그레이션·제약은 FareSplitPostgresTest 가 본다
 */
@DataJpaTest
@Import({FareSplitJpa.class, PartyJpa.class, JpaAuditingConfig.class})
class FareSplitJpaPersistenceTest {
    private static final Long PAYER_ID = 11L;
    private static final Long FIRST_ID = 12L;
    private static final Long SECOND_ID = 13L;
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");

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

    private void clearContext() {
        em.flush();
        em.clear();
    }

    // 실패한 INSERT 가 남은 영속성 컨텍스트는 flush 하지 않고 버린다
    private void clearContextQuietly() {
        em.clear();
    }
}
