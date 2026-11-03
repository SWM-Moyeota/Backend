package team.codingforest.moyeota.settlement.ledger;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementCloseServiceTest {
    private static final Long 기사 = 9L;
    private static final Long 다른_기사 = 10L;
    private static final Long 셋째_기사 = 11L;
    private static final SettlementPeriod 시월_12일_주 = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 12));
    private static final SettlementPeriod 시월_19일_주 = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 19));
    private static final SettlementPeriod 시월_26일_주 = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 26));
    private static final SettlementPeriod 십일월_2일_주 = SettlementPeriod.weekOf(LocalDate.of(2026, 11, 2));

    private FakeSettlements settlements;
    private FakeSettlementLedgers ledgers;
    private SettlementCloseService service;
    private long 결제그룹_순번;

    @BeforeEach
    void setUp() {
        settlements = new FakeSettlements();
        ledgers = new FakeSettlementLedgers(settlements);
        service = new SettlementCloseService(settlements, ledgers);
        결제그룹_순번 = 0;
    }

    @Test
    void 방금_끝난_주의_정산서를_확정_상태로_만들고_금액은_담은_원장의_합계다() {
        Long 월요일_운행 = 원장(기사, 시월_26일_주.start().plus(Duration.ofHours(12)), 17000);
        Long 일요일_운행 = 원장(기사, 시월_26일_주.end().minus(Duration.ofHours(1)), 9000);

        boolean created = service.close(기사, 시월_26일_주);

        Settlement settlement = 정산서(기사, 시월_26일_주);
        assertThat(created).isTrue();
        assertThat(settlements.count()).isEqualTo(1);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(settlement.getAmount()).isEqualTo(26000);
        assertThat(settlement.isPayable()).isTrue();
        assertThat(담긴_운행(settlement)).containsExactly(월요일_운행, 일요일_운행);
        assertThat(ledgers.sumSettlementAmount(settlement.getId())).isEqualTo(settlement.getAmount());
        assertThat(미정산_운행(기사)).isEmpty();
    }

    @Test
    void 일요일_KST_자정_직전_운행까지_담고_월요일_KST_자정_운행은_미정산으로_남긴다() {
        Long 일요일_자정_직전 = 원장(기사, Instant.parse("2026-11-01T14:59:59.999999Z"), 10000);
        Long 월요일_자정 = 원장(기사, Instant.parse("2026-11-01T15:00:00Z"), 20000);

        service.close(기사, 시월_26일_주);

        Settlement settlement = 정산서(기사, 시월_26일_주);
        assertThat(settlement.getAmount()).isEqualTo(10000);
        assertThat(담긴_운행(settlement)).containsExactly(일요일_자정_직전);
        assertThat(미정산_운행(기사)).containsExactly(월요일_자정);
    }

    @Test
    void 지난_주들에_끝났지만_아직_정산되지_않은_원장도_방금_끝난_주_정산서에_함께_담는다() {
        Long 삼주_전_운행 = 원장(기사, 시월_12일_주.start().plus(Duration.ofDays(2)), 7000);
        Long 이주_전_운행 = 원장(기사, 시월_19일_주.start().plus(Duration.ofDays(4)), 8000);
        Long 방금_끝난_주_운행 = 원장(기사, 시월_26일_주.start().plus(Duration.ofDays(1)), 9000);

        service.close(기사, 시월_26일_주);

        Settlement settlement = 정산서(기사, 시월_26일_주);
        assertThat(settlements.count()).isEqualTo(1);
        assertThat(settlement.getAmount()).isEqualTo(24000);
        assertThat(담긴_운행(settlement)).containsExactly(삼주_전_운행, 이주_전_운행, 방금_끝난_주_운행);
    }

    @Test
    void 이번_주에_끝난_원장은_미정산으로_남아_다음_주_배치를_기다린다() {
        원장(기사, 시월_26일_주.start().plus(Duration.ofDays(3)), 10000);
        Long 이번_주_운행 = 원장(기사, 십일월_2일_주.start().plus(Duration.ofHours(9)), 4000);

        service.close(기사, 시월_26일_주);

        assertThat(정산서(기사, 시월_26일_주).getAmount()).isEqualTo(10000);
        assertThat(미정산_운행(기사)).containsExactly(이번_주_운행);
        assertThat(settlements.findByDriverAndPeriod(기사, 십일월_2일_주)).isEmpty();
    }

    @Test
    void 그_주_정산서가_이미_있는_기사는_건너뛰고_그_뒤_들어온_늦은_원장은_다음_주_정산서에_담긴다() {
        Long 처음_운행 = 원장(기사, 시월_26일_주.start().plus(Duration.ofDays(2)), 15000);
        service.close(기사, 시월_26일_주);
        Long 늦은_운행 = 원장(기사, 시월_26일_주.end().minus(Duration.ofMinutes(10)), 6000);

        assertThat(service.findDriversToClose(시월_26일_주)).isEmpty();
        assertThat(service.close(기사, 시월_26일_주)).isFalse();

        Settlement 먼저_만든 = 정산서(기사, 시월_26일_주);
        assertThat(먼저_만든.getAmount()).isEqualTo(15000);
        assertThat(담긴_운행(먼저_만든)).containsExactly(처음_운행);
        assertThat(미정산_운행(기사)).containsExactly(늦은_운행);

        Long 다음_주_운행 = 원장(기사, 십일월_2일_주.start().plus(Duration.ofDays(1)), 3000);
        service.close(기사, 십일월_2일_주);

        Settlement 다음_주 = 정산서(기사, 십일월_2일_주);
        assertThat(다음_주.getAmount()).isEqualTo(9000);
        assertThat(담긴_운행(다음_주)).containsExactly(늦은_운행, 다음_주_운행);
        assertThat(정산서(기사, 시월_26일_주).getAmount()).isEqualTo(15000);
    }

    @Test
    void 방금_끝난_주까지의_미정산_원장이_있는_기사만_정산_대상이다() {
        원장(기사, 시월_26일_주.start().plus(Duration.ofDays(1)), 10000);
        원장(기사, 시월_19일_주.start().plus(Duration.ofDays(2)), 5000);
        원장(다른_기사, 십일월_2일_주.start().plus(Duration.ofDays(1)), 10000);
        원장(셋째_기사, 시월_19일_주.start().plus(Duration.ofDays(1)), 10000);
        service.close(셋째_기사, 시월_19일_주);

        assertThat(service.findDriversToClose(시월_26일_주)).containsExactly(기사);
    }

    @Test
    void 담을_미정산_원장이_없으면_예외를_낸다() {
        원장(기사, 십일월_2일_주.start().plus(Duration.ofDays(1)), 10000);

        assertThatThrownBy(() -> service.close(기사, 시월_26일_주)).isInstanceOf(IllegalStateException.class);
        assertThat(미정산_운행(기사)).hasSize(1);
    }

    @Test
    void 다시_돌려도_정산서를_중복으로_만들지_않고_금액도_그대로다() {
        원장(기사, 시월_26일_주.start().plus(Duration.ofDays(1)), 15000);

        assertThat(service.close(기사, 시월_26일_주)).isTrue();
        assertThat(service.close(기사, 시월_26일_주)).isFalse();

        assertThat(settlements.count()).isEqualTo(1);
        assertThat(정산서(기사, 시월_26일_주).getAmount()).isEqualTo(15000);
    }

    @Test
    void 정산_대상_기사를_id_순으로_모두_고른다() {
        원장(셋째_기사, 시월_26일_주.start().plus(Duration.ofDays(1)), 1000);
        원장(기사, 시월_26일_주.start().plus(Duration.ofDays(2)), 1000);
        원장(다른_기사, 시월_26일_주.start().plus(Duration.ofDays(3)), 1000);

        assertThat(service.findDriversToClose(시월_26일_주)).containsExactly(기사, 다른_기사, 셋째_기사);
    }

    @Nested
    class 정산서_생성_스케줄러 {
        private final Instant 오래전 = Instant.parse("2020-01-01T03:00:00Z");
        private final Instant 먼_미래 = Instant.parse("2999-01-01T03:00:00Z");

        @Test
        void 방금_끝난_주까지의_미정산_원장으로_기사마다_정산서를_만들고_기사가_여러_명이어도_모두_만든다() {
            int 기사_수 = 3;
            for(long driverId = 1; driverId <= 기사_수; driverId++) {
                원장(driverId, 오래전, 1000);
            }
            Long 미래_기사 = 기사_수 + 1L;
            Long 미래_운행 = 원장(미래_기사, 먼_미래, 5000);

            SettlementPeriod 전 = 방금_끝난_주();
            new SettlementCloseScheduler(service).close();
            SettlementPeriod 후 = 방금_끝난_주();

            assertThat(settlements.findAll())
                    .hasSize(기사_수)
                    .allSatisfy(s -> {
                        assertThat(s.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
                        assertThat(s.getAmount()).isEqualTo(1000);
                        assertThat(s.period()).isIn(전, 후);
                    });
            assertThat(미정산_운행(미래_기사)).containsExactly(미래_운행);
        }

        @Test
        void 한_기사에서_예외가_나도_나머지_기사의_정산서는_만들고_그_기사의_원장은_미정산으로_남는다() {
            원장(1L, 오래전, 1000);
            Long 고장_운행 = 원장(2L, 오래전, 2000);
            원장(3L, 오래전, 3000);
            SettlementCloseService 둘째_기사만_실패 = new SettlementCloseService(settlements, ledgers) {
                @Override
                public boolean close(Long taxiDriverId, SettlementPeriod week) {
                    if(taxiDriverId.equals(2L)) throw new IllegalStateException("정산서 생성 실패");
                    return super.close(taxiDriverId, week);
                }
            };

            new SettlementCloseScheduler(둘째_기사만_실패).close();

            assertThat(settlements.findAll()).extracting(Settlement::getTaxiDriverId).containsExactly(1L, 3L);
            assertThat(미정산_운행(2L)).containsExactly(고장_운행);
        }

        @Test
        @Timeout(5)
        void 모든_기사가_실패해도_기사마다_한_번씩만_시도하고_끝난다() {
            원장(1L, 오래전, 1000);
            원장(2L, 오래전, 1000);
            AtomicInteger 시도 = new AtomicInteger();
            SettlementCloseService 늘_실패 = new SettlementCloseService(settlements, ledgers) {
                @Override
                public boolean close(Long taxiDriverId, SettlementPeriod week) {
                    시도.incrementAndGet();
                    throw new IllegalStateException("정산서 생성 실패");
                }
            };

            new SettlementCloseScheduler(늘_실패).close();

            assertThat(시도).hasValue(2);
            assertThat(settlements.count()).isZero();
        }

        @Test
        void 방금_끝난_주까지_정산할_원장이_없으면_아무것도_만들지_않는다() {
            Long 미래_운행 = 원장(기사, 먼_미래, 5000);

            new SettlementCloseScheduler(service).close();

            assertThat(settlements.count()).isZero();
            assertThat(미정산_운행(기사)).containsExactly(미래_운행);
        }

        @Test
        void 월요일_KST_0시부터_9시까지_매시_5분에_잠금을_잡고_돌며_스케줄러에는_트랜잭션을_걸지_않는다() throws Exception {
            Method close = SettlementCloseScheduler.class.getDeclaredMethod("close");
            Scheduled scheduled = close.getAnnotation(Scheduled.class);
            SchedulerLock lock = close.getAnnotation(SchedulerLock.class);

            assertThat(scheduled.cron()).isEqualTo("0 5 0-9 * * MON");
            assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
            assertThat(lock.name()).isEqualTo("settlement-close");
            assertThat(Duration.parse(lock.lockAtMostFor())).isGreaterThan(Duration.parse(lock.lockAtLeastFor()));
            assertThat(Duration.parse(lock.lockAtMostFor())).as("매시 돌므로 잠금이 한 시간을 넘으면 다음 회차를 건너뛴다").isLessThan(Duration.ofHours(1));
            assertThat(close.isAnnotationPresent(Transactional.class)).isFalse();
            assertThat(SettlementCloseScheduler.class.isAnnotationPresent(Transactional.class)).isFalse();
        }

        @Test
        void 월요일_KST_0시_5분에_지난주_정산서를_만들고_같은_날_10시에_지급하며_지급은_매일_10시에_돈다() throws Exception {
            CronExpression 생성 = cronOf(SettlementCloseScheduler.class, "close");
            CronExpression 지급 = cronOf(PayoutScheduler.class, "pay");
            ZonedDateTime 일요일_자정_직전 = 시월_26일_주.end().minusNanos(1000).atZone(SettlementPeriod.ZONE);

            ZonedDateTime 첫_생성 = 생성.next(일요일_자정_직전);
            ZonedDateTime 첫_지급 = 지급.next(첫_생성);
            SettlementPeriod 만들_주 = SettlementPeriod.weekOf(첫_생성.toInstant()).previous();

            assertThat(첫_생성.toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 5));
            assertThat(만들_주).isEqualTo(시월_26일_주);
            assertThat(만들_주.end()).as("컷오프는 이번 주 월요일 KST 자정").isEqualTo(십일월_2일_주.start());
            assertThat(첫_지급.toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 11, 2, 10, 0));
            assertThat(지급.next(첫_지급).toLocalDateTime()).as("놓친 지급과 보류·실패 재시도는 다음 날 10시").isEqualTo(LocalDateTime.of(2026, 11, 3, 10, 0));
        }

        private SettlementPeriod 방금_끝난_주() {
            return SettlementPeriod.weekOf(Instant.now()).previous();
        }
    }

    private Long 원장(Long taxiDriverId, Instant occurredAt, int totalFare) {
        long paymentGroupId = ++결제그룹_순번;
        ledgers.save(SettlementLedger.record(taxiDriverId, paymentGroupId, totalFare, 0, occurredAt));
        return paymentGroupId;
    }

    private Settlement 정산서(Long taxiDriverId, SettlementPeriod week) {
        return settlements.findByDriverAndPeriod(taxiDriverId, week).orElseThrow();
    }

    private List<Long> 담긴_운행(Settlement settlement) {
        return ledgers.findBySettlementId(settlement.getId()).stream().map(SettlementLedger::getPaymentGroupId).toList();
    }

    private List<Long> 미정산_운행(Long taxiDriverId) {
        return ledgers.findUnsettledByDriver(taxiDriverId).stream().map(SettlementLedger::getPaymentGroupId).toList();
    }

    private static CronExpression cronOf(Class<?> scheduler, String method) throws NoSuchMethodException {
        return CronExpression.parse(scheduler.getDeclaredMethod(method).getAnnotation(Scheduled.class).cron());
    }
}
