package team.codingforest.moyeota.settlement.ledger.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.ride.RideService;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.settlement.api.SettlementAccess;
import team.codingforest.moyeota.settlement.api.dto.RideSettlementCommand;
import team.codingforest.moyeota.settlement.ledger.SettlementCloseService;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Payouts;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedgers;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.profiles.active=dev")
class SettlementPostgresTest {
    private static final int 넉넉한_한도 = 10_000;
    private static final BankCode 은행 = BankCode.values()[0];
    private static final String 계좌번호 = "110123456789";
    private static final String 예금주 = "김기사";
    private static final String 정산서_생성_배치가_닿지_않는_해 = "2999";

    @Autowired Settlements settlements;
    @Autowired SettlementLedgers ledgers;
    @Autowired Payouts payouts;
    @Autowired SettlementAccess settlementAccess;
    @Autowired SettlementCloseService closeService;
    @Autowired RideService rideService;
    @Autowired Parties parties;
    @Autowired PartyAccess partyAccess;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private final Long 기사 = ThreadLocalRandom.current().nextLong(3_000_000_000L, 4_000_000_000L);
    private final Long 다른기사 = 기사 + 1;
    private final Long 셋째기사 = 기사 + 2;
    private final Long 넷째기사 = 기사 + 3;
    private final Long 승객 = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
    private final long 운행번호 = ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 2_000_000_000_000L);
    private final List<Long> 만든_방 = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() throws InterruptedException {
        pool.shutdownNow();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        만든_방.forEach(partyId -> {
            jdbc.update("DELETE FROM settlement_ledger WHERE match_id = ?", partyId);
            jdbc.update("DELETE FROM ride_record WHERE match_id = ?", partyId);
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", partyId);
            jdbc.update("DELETE FROM match_room WHERE id = ?", partyId);
        });
        jdbc.update("DELETE FROM payout WHERE settlement_id IN (SELECT id FROM settlement WHERE taxi_driver_id BETWEEN ? AND ?)", 기사, 넷째기사);
        jdbc.update("DELETE FROM settlement_ledger WHERE taxi_driver_id BETWEEN ? AND ?", 기사, 넷째기사);
        jdbc.update("DELETE FROM settlement WHERE taxi_driver_id BETWEEN ? AND ?", 기사, 넷째기사);
    }

    @Test
    void 기사가_운행을_마치면_운행_종료와_정산_원장_기록이_함께_커밋된다() {
        Long partyId = 운행_중인_방();

        rideService.complete(partyId, 기사, 15_000);

        assertThat(jdbc.queryForObject("SELECT total_fare FROM ride_record WHERE match_id = ?", Integer.class, partyId)).isEqualTo(15_000);
        assertThat(ledgers.findUnsettledByDriver(기사)).singleElement().satisfies(ledger -> {
            assertThat(ledger.getMatchId()).isEqualTo(partyId);
            assertThat(ledger.getTaxiDriverId()).isEqualTo(기사);
            assertThat(ledger.getTotalFare()).isEqualTo(15_000);
            assertThat(ledger.getPlatformPromotion()).isZero();
            assertThat(ledger.getSettlementAmount()).isEqualTo(15_000);
            assertThat(ledger.getSettlementId()).isNull();
        });
    }

    @Test
    void 이미_끝난_운행을_다시_끝내려_하면_예외이고_정산_원장은_늘지_않는다() {
        Long partyId = 운행_중인_방();
        rideService.complete(partyId, 기사, 15_000);

        assertThatThrownBy(() -> rideService.complete(partyId, 기사, 20_000)).isInstanceOf(BusinessException.class);

        assertThat(ledgers.findUnsettledByDriver(기사)).singleElement()
                .extracting(SettlementLedger::getTotalFare).isEqualTo(15_000);
    }

    @Test
    void 운행을_기록하면_기사의_미정산_원장으로_남고_같은_운행은_한_번만_남는다() {
        Instant 운행끝 = 미래_주("03-02").start().plusSeconds(3_600);
        RideSettlementCommand command = new RideSettlementCommand(운행번호, 기사, 12_000, 500, 운행끝);

        settlementAccess.record(command);
        settlementAccess.record(command);

        assertThat(ledgers.findUnsettledByDriver(기사)).singleElement().satisfies(ledger -> {
            assertThat(ledger.getMatchId()).isEqualTo(운행번호);
            assertThat(ledger.getTaxiDriverId()).isEqualTo(기사);
            assertThat(ledger.getSettlementId()).isNull();
            assertThat(ledger.getSettlementAmount()).isEqualTo(12_500);
            assertThat(ledger.getOccurredAt()).isEqualTo(운행끝);
            assertThat(ledger.getCreatedAt()).isNotNull();
        });
        assertThat(countSettlements(기사)).isZero();
    }

    @Test
    void 같은_운행의_원장은_다른_기사로도_다시_저장할_수_없다() {
        Instant 운행끝 = 미래_주("03-02").start().plusSeconds(3_600);
        ledgers.save(원장(기사, 운행번호, 10_000, 운행끝));

        assertThat(ledgers.existsByMatchId(운행번호)).isTrue();
        assertThatThrownBy(() -> ledgers.save(원장(다른기사, 운행번호, 10_000, 운행끝.plusSeconds(60))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 정산_대상은_컷오프_전_미정산_원장이_있고_그_주_정산서가_없는_기사를_id_순으로_고른다() {
        SettlementPeriod 한_주 = 미래_주("03-09");
        SettlementPeriod 앞_주 = 한_주.previous();
        ledgers.save(원장(기사, 운행번호, 1_000, 한_주.end().minus(1, ChronoUnit.MICROS)));
        ledgers.save(원장(기사, 운행번호 + 5, 1_000, 앞_주.start()));
        Long 다른기사_앞_주_정산서 = settlement(다른기사, 앞_주, SettlementStatus.CONFIRMED, 1_000);
        ledgers.save(원장(다른기사, 운행번호 + 1, 1_000, 앞_주.start()));
        ledgers.assignToSettlement(다른기사, 앞_주.end(), 다른기사_앞_주_정산서);
        ledgers.save(원장(다른기사, 운행번호 + 2, 1_000, 한_주.end()));
        ledgers.save(원장(셋째기사, 운행번호 + 3, 1_000, 한_주.start()));
        settlement(셋째기사, 한_주, SettlementStatus.CONFIRMED, 1_000);
        ledgers.save(원장(넷째기사, 운행번호 + 4, 1_000, 앞_주.start()));

        List<Long> 이번_기사들 = List.of(기사, 다른기사, 셋째기사, 넷째기사);
        assertThat(ledgers.findDriverIdsToSettle(한_주, 0L, 넉넉한_한도))
                .filteredOn(이번_기사들::contains)
                .containsExactly(기사, 넷째기사);
        assertThat(ledgers.findDriverIdsToSettle(한_주, 기사, 넉넉한_한도))
                .as("afterDriverId 보다 큰 기사만 이어서 읽는다")
                .filteredOn(이번_기사들::contains)
                .containsExactly(넷째기사);
    }

    @Test
    void 정산서에_원장을_연결하면_컷오프_전_그_기사의_미정산_원장만_연결하고_연결한_수를_돌려준다() {
        SettlementPeriod 한_주 = 미래_주("03-16");
        Long 정산서 = settlement(기사, 한_주, SettlementStatus.CONFIRMED, 0);
        ledgers.save(원장(기사, 운행번호, 1_000, 한_주.previous().start().plusSeconds(60)));
        ledgers.save(원장(기사, 운행번호 + 1, 2_000, 한_주.start()));
        ledgers.save(원장(기사, 운행번호 + 2, 3_000, 한_주.end().minus(1, ChronoUnit.MICROS)));
        ledgers.save(원장(기사, 운행번호 + 3, 4_000, 한_주.end()));
        ledgers.save(원장(다른기사, 운행번호 + 4, 5_000, 한_주.start()));

        int 연결 = ledgers.assignToSettlement(기사, 한_주.end(), 정산서);

        assertThat(연결).isEqualTo(3);
        assertThat(ledgers.findBySettlementId(정산서)).extracting(SettlementLedger::getMatchId)
                .containsExactly(운행번호, 운행번호 + 1, 운행번호 + 2);
        assertThat(ledgers.sumSettlementAmount(정산서)).isEqualTo(6_000);
        assertThat(ledgers.findUnsettledByDriver(기사)).extracting(SettlementLedger::getMatchId).containsExactly(운행번호 + 3);
        assertThat(ledgers.findUnsettledByDriver(다른기사)).extracting(SettlementLedger::getMatchId).containsExactly(운행번호 + 4);
        assertThat(ledgers.assignToSettlement(기사, 한_주.end(), 정산서)).as("이미 연결한 원장은 다시 연결하지 않는다").isZero();
    }

    @Test
    void 정산서를_만드는_사이_들어온_원장은_연결되지_않고_정산서_금액은_연결된_원장_합계와_같다() throws Exception {
        SettlementPeriod 한_주 = 미래_주("03-23");
        ledgers.save(원장(기사, 운행번호, 5_000, 한_주.start().plusSeconds(3_600)));
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> 늦은_기록 = pool.submit(() -> tx.executeWithoutResult(status -> {
            ledgers.save(원장(기사, 운행번호 + 1, 1_000, 한_주.start().plusSeconds(7_200)));
            inserted.countDown();
            awaitQuietly(release);
        }));
        assertThat(inserted.await(10, TimeUnit.SECONDS)).as("늦은 원장을 넣은 트랜잭션이 아직 커밋하지 않았다").isTrue();

        boolean created;
        try {
            created = pool.submit(() -> closeService.close(기사, 한_주)).get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        늦은_기록.get(10, TimeUnit.SECONDS);

        Settlement 정산서 = settlementOf(기사, 한_주);
        assertThat(created).isTrue();
        assertThat(정산서.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(정산서.getAmount()).isEqualTo(5_000).isEqualTo(ledgers.sumSettlementAmount(정산서.getId()));
        assertThat(ledgers.findUnsettledByDriver(기사)).extracting(SettlementLedger::getMatchId).containsExactly(운행번호 + 1);
    }

    @Test
    void 담을_미정산_원장이_없으면_정산서를_만들지_않고_되돌린다() {
        SettlementPeriod 한_주 = 미래_주("03-30");
        ledgers.save(원장(기사, 운행번호, 1_000, 한_주.end()));

        assertThatThrownBy(() -> closeService.close(기사, 한_주)).isInstanceOf(IllegalStateException.class);

        assertThat(countSettlements(기사)).isZero();
        assertThat(ledgers.findUnsettledByDriver(기사)).hasSize(1);
    }

    @Test
    void 그_주_정산서가_이미_있으면_다시_만들지_않고_그_뒤_들어온_원장은_미정산으로_남는다() {
        SettlementPeriod 한_주 = 미래_주("04-06");
        ledgers.save(원장(기사, 운행번호, 1_000, 한_주.start()));
        assertThat(closeService.close(기사, 한_주)).isTrue();
        ledgers.save(원장(기사, 운행번호 + 1, 2_000, 한_주.start().plusSeconds(60)));

        assertThat(closeService.close(기사, 한_주)).isFalse();

        assertThat(countSettlements(기사)).isEqualTo(1);
        assertThat(settlementOf(기사, 한_주).getAmount()).isEqualTo(1_000);
        assertThat(ledgers.findUnsettledByDriver(기사)).extracting(SettlementLedger::getMatchId).containsExactly(운행번호 + 1);
    }

    @Test
    void 기사와_주마다_정산서는_하나뿐이다() {
        SettlementPeriod 한_주 = 미래_주("04-13");
        settlement(기사, 한_주, SettlementStatus.CONFIRMED, 1_000);

        assertThatThrownBy(() -> settlement(기사, 한_주, SettlementStatus.CONFIRMED, 2_000))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countSettlements(기사)).isEqualTo(1);
    }

    @Test
    void 원장은_운행_시각_순으로_읽고_합계는_원장이_없으면_0이다() {
        SettlementPeriod 한_주 = 미래_주("04-20");
        Long 정산서 = settlement(기사, 한_주, SettlementStatus.CONFIRMED, 0);
        Long 빈정산서 = settlement(기사, 미래_주("04-27"), SettlementStatus.CONFIRMED, 0);
        Instant 정오 = 한_주.start().plus(Duration.ofHours(12));
        Long 늦은 = ledgers.save(원장(기사, 운행번호, 3_000, 정오.plusSeconds(600))).getId();
        Long 이른 = ledgers.save(원장(기사, 운행번호 + 1, 2_000, 정오)).getId();
        Long 같은시각 = ledgers.save(원장(기사, 운행번호 + 2, 1_000, 정오)).getId();

        assertThat(ledgers.findUnsettledByDriver(기사)).extracting(SettlementLedger::getId).containsExactly(이른, 같은시각, 늦은);

        ledgers.assignToSettlement(기사, 한_주.end(), 정산서);

        assertThat(ledgers.findBySettlementId(정산서)).extracting(SettlementLedger::getId).containsExactly(이른, 같은시각, 늦은);
        assertThat(ledgers.sumSettlementAmount(정산서)).isEqualTo(6_000);
        assertThat(ledgers.sumSettlementAmount(빈정산서)).isZero();
    }

    @Test
    void 기사와_주로_정산서를_찾고_없으면_비어_있다() {
        SettlementPeriod 이번_주 = 주("2026-03-25");
        Long 내정산서 = settlement(기사, 이번_주, SettlementStatus.CONFIRMED, 1_000);
        Long 다음주_정산서 = settlement(기사, 주("2026-04-01"), SettlementStatus.CONFIRMED, 0);
        Long 남의정산서 = settlement(다른기사, 이번_주, SettlementStatus.CONFIRMED, 2_000);

        assertThat(settlements.findByDriverAndPeriod(기사, 이번_주)).map(Settlement::getId).contains(내정산서);
        assertThat(settlements.findByDriverAndPeriod(기사, 주("2026-04-01"))).map(Settlement::getId).contains(다음주_정산서);
        assertThat(settlements.findByDriverAndPeriod(다른기사, 이번_주)).map(Settlement::getId).contains(남의정산서);
        assertThat(settlements.findByDriverAndPeriod(기사, 주("2026-03-18"))).isEmpty();
    }

    @Test
    void 주로_정산서를_찾을_때는_잠그지_않아_다른_트랜잭션이_잠가_둬도_기다리지_않는다() throws Exception {
        SettlementPeriod 이번_주 = 주("2026-03-25");
        Long 내정산서 = settlement(기사, 이번_주, SettlementStatus.CONFIRMED, 1_000);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
            settlements.findByIdForUpdate(내정산서);
            locked.countDown();
            awaitQuietly(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).as("다른 트랜잭션이 정산서를 잠갔다").isTrue();

        try {
            Future<Optional<Settlement>> found = pool.submit(() -> settlements.findByDriverAndPeriod(기사, 이번_주));
            assertThat(found.get(5, TimeUnit.SECONDS)).map(Settlement::getId).contains(내정산서);
        } finally {
            release.countDown();
        }
        holder.get(10, TimeUnit.SECONDS);
    }

    @Test
    void 지급_대상은_금액이_있고_진행_중인_지급이_없으며_실패가_한도_미만인_정산서다() {
        Long 확정 = settlement(기사, 주("2026-04-06"), SettlementStatus.CONFIRMED, 1_000);
        Long 보류 = settlement(기사, 주("2026-04-13"), SettlementStatus.HOLD, 1_000);
        Long 두번실패 = settlement(기사, 주("2026-04-20"), SettlementStatus.FAILED, 1_000);
        Long 세번실패 = settlement(기사, 주("2026-04-27"), SettlementStatus.FAILED, 1_000);
        Long 지급중 = settlement(기사, 주("2026-05-04"), SettlementStatus.CONFIRMED, 1_000);
        Long 결과모름 = settlement(기사, 주("2026-05-11"), SettlementStatus.CONFIRMED, 1_000);
        Long 지급완료 = settlement(기사, 주("2026-05-18"), SettlementStatus.PAID, 1_000);
        Long 금액없음 = settlement(기사, 주("2026-05-25"), SettlementStatus.CONFIRMED, 0);
        payout(두번실패, PayoutStatus.FAILED);
        payout(두번실패, PayoutStatus.FAILED);
        payout(세번실패, PayoutStatus.FAILED);
        payout(세번실패, PayoutStatus.FAILED);
        payout(세번실패, PayoutStatus.FAILED);
        payout(지급중, PayoutStatus.REQUESTED);
        payout(결과모름, PayoutStatus.UNKNOWN);
        payout(지급완료, PayoutStatus.SUCCEED);

        List<Long> 이번_기사 = List.of(확정, 보류, 두번실패, 세번실패, 지급중, 결과모름, 지급완료, 금액없음);
        assertThat(settlements.findPayableIds(3, 0L, 넉넉한_한도))
                .filteredOn(이번_기사::contains)
                .containsExactly(확정, 보류, 두번실패);
        assertThat(settlements.findPayableIds(3, 확정, 넉넉한_한도))
                .as("afterId 보다 큰 id 만 이어서 읽는다")
                .filteredOn(이번_기사::contains)
                .containsExactly(보류, 두번실패);
    }

    @Test
    void 정산서_하나에_진행_중인_지급을_두_건_저장할_수_없다() {
        Long 정산서 = settlement(기사, 주("2026-05-01"), SettlementStatus.CONFIRMED, 1_000);
        payout(정산서, PayoutStatus.REQUESTED);

        assertThatThrownBy(() -> payout(정산서, PayoutStatus.UNKNOWN)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(payouts.existsActiveBySettlementId(정산서)).isTrue();
    }

    @Test
    void 성공한_지급이_있으면_새_지급을_저장할_수_없다() {
        Long 정산서 = settlement(기사, 주("2026-05-02"), SettlementStatus.PAID, 1_000);
        payout(정산서, PayoutStatus.SUCCEED);

        assertThatThrownBy(() -> payout(정산서, PayoutStatus.REQUESTED)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 지급이_실패로_바뀌면_같은_정산서에_새_지급을_저장할_수_있다() {
        Long 정산서 = settlement(기사, 주("2026-05-03"), SettlementStatus.CONFIRMED, 1_000);
        Long 첫지급 = payout(정산서, PayoutStatus.REQUESTED);
        tx.executeWithoutResult(status -> {
            Payout payout = payouts.findByIdForUpdate(첫지급).orElseThrow();
            payout.fail("잔액 부족");
            payouts.save(payout);
        });
        payout(정산서, PayoutStatus.FAILED);

        Long 새지급 = payout(정산서, PayoutStatus.REQUESTED);

        assertThat(payouts.countFailedBySettlementId(정산서)).isEqualTo(2);
        assertThat(payouts.existsActiveBySettlementId(정산서)).isTrue();
        assertThat(payouts.findLatestBySettlementId(정산서).orElseThrow().getId()).isEqualTo(새지급);
        assertThat(payouts.findById(첫지급).orElseThrow())
                .extracting(Payout::getStatus, Payout::getFailReason, Payout::getBankCode, Payout::getAccountNumber)
                .containsExactly(PayoutStatus.FAILED, "잔액 부족", 은행, 계좌번호);
    }

    @Test
    void 대사_대상은_오래된_요청과_결과를_모르는_지급이다() {
        Instant 기준 = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant 요청기한 = 기준.minus(10, ChronoUnit.MINUTES);
        Long 오래된요청 = payoutCreatedAt(주("2026-06-01"), PayoutStatus.REQUESTED, 요청기한.minusSeconds(1));
        Long 기한과같은요청 = payoutCreatedAt(주("2026-06-08"), PayoutStatus.REQUESTED, 요청기한);
        Long 최근요청 = payoutCreatedAt(주("2026-06-15"), PayoutStatus.REQUESTED, 기준);
        Long 결과모름 = payoutCreatedAt(주("2026-06-22"), PayoutStatus.UNKNOWN, 기준);
        Long 성공 = payoutCreatedAt(주("2026-06-29"), PayoutStatus.SUCCEED, 요청기한.minusSeconds(3_600));
        Long 실패 = payoutCreatedAt(주("2026-07-06"), PayoutStatus.FAILED, 요청기한.minusSeconds(3_600));

        List<Long> 이번_기사 = List.of(오래된요청, 기한과같은요청, 최근요청, 결과모름, 성공, 실패);
        assertThat(payouts.findStaleIds(요청기한, 0L, 넉넉한_한도))
                .filteredOn(이번_기사::contains)
                .containsExactly(오래된요청, 결과모름);
        assertThat(payouts.findStaleIds(요청기한, 오래된요청, 넉넉한_한도))
                .as("afterId 보다 큰 id 만 이어서 읽는다")
                .filteredOn(이번_기사::contains)
                .containsExactly(결과모름);
    }

    private Long settlement(Long taxiDriverId, SettlementPeriod period, SettlementStatus status, int amount) {
        return settlements.save(Settlement.load(null, taxiDriverId, period.start(), period.end(), amount, status, null, null)).getId();
    }

    private Long payout(Long settlementId, PayoutStatus status) {
        String pgId = status == PayoutStatus.SUCCEED ? "pg-" + settlementId : null;
        String failReason = status == PayoutStatus.FAILED ? "잔액 부족" : null;
        return payouts.save(Payout.load(null, settlementId, 1_000, status, pgId, 은행, 계좌번호, 예금주, failReason, null, null)).getId();
    }

    private Long payoutCreatedAt(SettlementPeriod period, PayoutStatus status, Instant createdAt) {
        Long payoutId = payout(settlement(기사, period, SettlementStatus.CONFIRMED, 1_000), status);
        jdbc.update("UPDATE payout SET created_at = ? WHERE id = ?", createdAt.atOffset(ZoneOffset.UTC), payoutId);
        return payoutId;
    }

    private Settlement settlementOf(Long taxiDriverId, SettlementPeriod period) {
        return settlements.findByDriverAndPeriod(taxiDriverId, period).orElseThrow();
    }

    private int countSettlements(Long taxiDriverId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM settlement WHERE taxi_driver_id = ?", Integer.class, taxiDriverId);
        return count == null ? 0 : count;
    }

    private static SettlementLedger 원장(Long taxiDriverId, long matchId, int totalFare, Instant occurredAt) {
        return SettlementLedger.record(taxiDriverId, matchId, totalFare, 0, occurredAt);
    }

    private Long 운행_중인_방() {
        Party party = Party.open(승객, new Location(37.4979, 127.0276), new Location(37.3948, 127.1112), "강남역", "판교역",
                new Capacity(2), Instant.now(), new Radius(100), new Radius(100), 12000, 25, "_p~iF~ps|U_ulLnnqC");
        party.join(승객 + 1);
        party.startMatching(Instant.now());
        Long partyId = parties.save(party).getId();
        만든_방.add(partyId);

        partyAccess.assignDriver(partyId, 기사);
        partyAccess.startRide(partyId, 기사);
        return partyId;
    }

    private static SettlementPeriod 주(String date) {
        return SettlementPeriod.weekOf(LocalDate.parse(date));
    }

    private static SettlementPeriod 미래_주(String monthDay) {
        return 주(정산서_생성_배치가_닿지_않는_해 + "-" + monthDay);
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
