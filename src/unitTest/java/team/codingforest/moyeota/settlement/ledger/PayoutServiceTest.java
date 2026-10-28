package team.codingforest.moyeota.settlement.ledger;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutAccount;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutOrder;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutResult;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutServiceTest {
    private static final Long 기사 = 9L;
    private static final Long 다른기사 = 10L;
    private static final LocalDate 첫_주 = LocalDate.of(2026, 10, 5);
    private static final Instant 요청시각 = Instant.parse("2026-10-29T01:00:00.123456Z");
    private static final Duration 대사_대기 = Duration.ofMinutes(10);

    private FakePayouts payouts;
    private FakeSettlements settlements;
    private FakePayoutAccounts accounts;
    private FakePayoutGateway gateway;
    private PayoutSteps steps;
    private PayoutService service;
    private int 주_순번 = 0;

    @BeforeEach
    void setUp() {
        payouts = new FakePayouts();
        settlements = new FakeSettlements(payouts);
        accounts = new FakePayoutAccounts();
        gateway = new FakePayoutGateway();
        steps = new PayoutSteps(settlements, payouts, accounts);
        service = new PayoutService(settlements, payouts, gateway, steps);
    }

    @Test
    void 지급에_성공하면_지급은_SUCCEED_이고_pgId_를_남기며_정산서는_PAID_가_된다() {
        Long 정산서 = 확정(기사, 32_000);
        accounts.register(기사);

        int 요청건수 = service.payDue();

        Payout 지급 = 유일한_지급(정산서);
        assertThat(요청건수).isEqualTo(1);
        assertThat(지급.getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(지급.getPgId()).isEqualTo(FakePayoutGateway.DEFAULT_PG_ID_PREFIX + 지급.getId());
        assertThat(지급.getAmount()).isEqualTo(32_000);
        assertThat(지급.getFailReason()).isNull();
        assertThat(지급.account()).isEqualTo(FakePayoutAccounts.sampleAccount());
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void 결제사에_넘기는_지급_요청에는_정산_금액과_기사_계좌와_지급_키가_정확히_담긴다() {
        PayoutAccount 계좌 = new PayoutAccount(FakePayoutAccounts.SAMPLE_BANK, "3333012345678", "박기사");
        Long 정산서 = 확정(기사, 45_000);
        accounts.register(기사, 계좌);

        service.payDue();

        Payout 지급 = 유일한_지급(정산서);
        assertThat(gateway.requests()).containsExactly(
                new PayoutOrder(지급.getId(), "moyeota-payout-" + 지급.getId(), 45_000, 계좌));
        assertThat(지급.getBankCode()).isEqualTo(FakePayoutAccounts.SAMPLE_BANK);
        assertThat(지급.getAccountNumber()).isEqualTo("3333012345678");
        assertThat(지급.getAccountHolder()).isEqualTo("박기사");
    }

    @Test
    void 지급에_실패하면_지급은_FAILED_이고_사유를_남기며_정산서는_FAILED_가_되고_같은_배치에서는_다시_시도하지_않는다() {
        Long 정산서 = 확정(기사, 20_000);
        accounts.register(기사);
        gateway.willReturn(new PayoutResult.Failed("예금주 불일치"));

        int 요청건수 = service.payDue();

        Payout 지급 = 유일한_지급(정산서);
        assertThat(요청건수).isEqualTo(1);
        assertThat(gateway.requestCount()).isEqualTo(1);
        assertThat(지급.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(지급.getFailReason()).isEqualTo("예금주 불일치");
        assertThat(지급.getPgId()).isNull();
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    void 실패한_정산서는_다음_배치에서_새_지급_키로_다시_지급한다() {
        Long 정산서 = 확정(기사, 20_000);
        accounts.register(기사);
        gateway.willReturn(new PayoutResult.Failed("은행 점검"));

        service.payDue();
        int 다음_요청건수 = service.payDue();

        List<Payout> 지급들 = payouts.findBySettlementId(정산서);
        assertThat(다음_요청건수).isEqualTo(1);
        assertThat(지급들).extracting(Payout::getStatus).containsExactly(PayoutStatus.FAILED, PayoutStatus.SUCCEED);
        assertThat(gateway.requests()).extracting(PayoutOrder::payoutKey).doesNotHaveDuplicates().hasSize(2);
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void 실패한_지급이_3건_쌓이면_더는_지급하지_않는다() {
        Long 정산서 = 확정(기사, 20_000);
        accounts.register(기사);
        gateway.willReturn(new PayoutResult.Failed("한도 초과"), new PayoutResult.Failed("한도 초과"), new PayoutResult.Failed("한도 초과"));

        for(int i = 0; i < PayoutSteps.MAX_PAYOUT_ATTEMPTS; i++) {
            assertThat(service.payDue()).isEqualTo(1);
        }
        int 네번째_요청건수 = service.payDue();

        assertThat(네번째_요청건수).isZero();
        assertThat(gateway.requestCount()).isEqualTo(PayoutSteps.MAX_PAYOUT_ATTEMPTS);
        assertThat(payouts.findBySettlementId(정산서)).extracting(Payout::getStatus)
                .containsExactly(PayoutStatus.FAILED, PayoutStatus.FAILED, PayoutStatus.FAILED);
        assertThat(steps.prepare(정산서)).isEmpty();
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    void 지급_계좌가_없으면_정산서를_HOLD_로_두고_결제사를_부르지_않는다() {
        Long 정산서 = 확정(기사, 15_000);

        int 요청건수 = service.payDue();

        assertThat(요청건수).isZero();
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.HOLD);
        assertThat(payouts.findBySettlementId(정산서)).isEmpty();
        assertThat(gateway.requestCount()).isZero();
    }

    @Test
    void 보류된_정산서는_계좌가_생기면_다음_배치에서_지급된다() {
        Long 정산서 = 확정(기사, 15_000);
        service.payDue();
        service.payDue();
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.HOLD);

        accounts.register(기사);
        int 요청건수 = service.payDue();

        Payout 지급 = 유일한_지급(정산서);
        assertThat(요청건수).isEqualTo(1);
        assertThat(지급.getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(지급.getAmount()).isEqualTo(15_000);
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void 결제사_호출이_예외면_지급은_UNKNOWN_이고_정산서는_CONFIRMED_로_남으며_다음_배치가_다시_지급하지_않는다() {
        Long 정산서 = 확정(기사, 10_000);
        accounts.register(기사);
        gateway.willThrow(new IllegalStateException("read timeout"));

        int 첫_요청건수 = service.payDue();
        int 다음_요청건수 = service.payDue();

        Payout 지급 = 유일한_지급(정산서);
        assertThat(첫_요청건수).isEqualTo(1);
        assertThat(다음_요청건수).isZero();
        assertThat(gateway.requestCount()).isEqualTo(1);
        assertThat(지급.getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(지급.getFailReason()).startsWith("지급 요청 결과를 받지 못함").contains("IllegalStateException");
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.CONFIRMED);
    }

    @Test
    void 결제사가_빈_응답을_주면_지급은_UNKNOWN_으로_두고_다음_정산서는_계속_지급한다() {
        Long 빈응답 = 확정(기사, 10_000);
        Long 다음 = 확정(다른기사, 20_000);
        accounts.register(기사).register(다른기사);
        gateway.willReturn((PayoutResult) null);

        int 요청건수 = service.payDue();
        int 다음_요청건수 = service.payDue();

        assertThat(요청건수).isEqualTo(2);
        assertThat(다음_요청건수).isZero();
        assertThat(gateway.requestCount()).isEqualTo(2);
        assertThat(유일한_지급(빈응답).getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(상태(빈응답)).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(유일한_지급(다음).getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(상태(다음)).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void 결제사가_결과를_모른다고_답하면_지급은_UNKNOWN_으로_두고_사유를_남긴다() {
        Long 정산서 = 확정(기사, 10_000);
        accounts.register(기사);
        gateway.willReturn(new PayoutResult.Unknown("은행 응답 지연"));

        service.payDue();

        Payout 지급 = 유일한_지급(정산서);
        assertThat(지급.getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(지급.getFailReason()).isEqualTo("은행 응답 지연");
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.CONFIRMED);
    }

    @Test
    void 대사는_결과를_모르는_지급을_결제사_조회_결과가_성공이면_SUCCEED_로_확정하고_정산서는_PAID_가_된다() {
        Long 정산서 = 확정(기사, 10_000);
        accounts.register(기사);
        payouts.setNow(요청시각);
        Payout 지급 = 결과를_모르는_지급(정산서);
        gateway.willFind(지급.payoutKey(), new PayoutResult.Succeeded("pg-777"));

        int 확정건수 = service.reconcile(요청시각.plus(Duration.ofMinutes(1)));

        Payout 확정된 = 유일한_지급(정산서);
        assertThat(확정건수).isEqualTo(1);
        assertThat(gateway.findCalls()).containsExactly(지급.payoutKey());
        assertThat(확정된.getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(확정된.getPgId()).isEqualTo("pg-777");
        assertThat(확정된.getFailReason()).isNull();
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.PAID);
        assertThat(gateway.requestCount()).isZero();
    }

    @Test
    void 대사는_결과를_모르는_지급을_결제사_조회_결과가_실패면_FAILED_로_확정하고_다음_배치에서_다시_지급한다() {
        Long 정산서 = 확정(기사, 10_000);
        accounts.register(기사);
        payouts.setNow(요청시각);
        Payout 지급 = 결과를_모르는_지급(정산서);
        gateway.willFind(지급.payoutKey(), new PayoutResult.Failed("잔액 부족"));

        int 확정건수 = service.reconcile(요청시각.plus(Duration.ofMinutes(1)));

        Payout 확정된 = 유일한_지급(정산서);
        assertThat(확정건수).isEqualTo(1);
        assertThat(확정된.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(확정된.getFailReason()).isEqualTo("잔액 부족");
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.FAILED);

        int 요청건수 = service.payDue();

        assertThat(요청건수).isEqualTo(1);
        assertThat(payouts.findBySettlementId(정산서)).extracting(Payout::getStatus)
                .containsExactly(PayoutStatus.FAILED, PayoutStatus.SUCCEED);
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void 결제사에_기록이_없고_10분이_지난_UNKNOWN_지급은_같은_지급_키로_다시_요청해_결과를_확정한다() {
        Long 정산서 = 확정(기사, 10_000);
        accounts.register(기사);
        payouts.setNow(요청시각);
        Payout 지급 = 결과를_모르는_지급(정산서);
        int 앞선_요청 = gateway.requestCount();

        int 확정건수 = service.reconcile(요청시각.plus(대사_대기).plusNanos(1_000));

        Payout 확정된 = 유일한_지급(정산서);
        PayoutOrder 다시_보낸_요청 = gateway.requests().getLast();
        assertThat(확정건수).isEqualTo(1);
        assertThat(gateway.findCalls()).containsExactly(지급.payoutKey());
        assertThat(gateway.requestCount()).isEqualTo(앞선_요청 + 1);
        assertThat(다시_보낸_요청).isEqualTo(new PayoutOrder(지급.getId(), 지급.payoutKey(), 10_000, FakePayoutAccounts.sampleAccount()));
        assertThat(확정된.getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.PAID);
        assertThat(payouts.findBySettlementId(정산서)).hasSize(1);
    }

    @Test
    void 결제사에_기록이_없고_10분이_지난_REQUESTED_지급은_같은_지급_키로_다시_요청하고_실패_응답이면_실패로_확정한다() {
        Long 정산서 = 확정(기사, 10_000);
        accounts.register(기사);
        payouts.setNow(요청시각);
        Payout 지급 = 요청만_기록된_지급(정산서);
        gateway.willReturn(new PayoutResult.Failed("계좌 오류"));

        int 확정건수 = service.reconcile(요청시각.plus(대사_대기).plusNanos(1_000));

        Payout 확정된 = 유일한_지급(정산서);
        assertThat(확정건수).isEqualTo(1);
        assertThat(gateway.findCalls()).containsExactly(지급.payoutKey());
        assertThat(gateway.requests()).extracting(PayoutOrder::payoutKey).containsExactly(지급.payoutKey());
        assertThat(확정된.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(확정된.getFailReason()).isEqualTo("계좌 오류");
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    void 다시_요청해도_결과를_모르면_UNKNOWN_으로_두고_새_지급을_만들지_않는다() {
        Long 정산서 = 확정(기사, 10_000);
        accounts.register(기사);
        payouts.setNow(요청시각);
        Payout 지급 = 요청만_기록된_지급(정산서);
        gateway.willThrow(new IllegalStateException("결제사 타임아웃"));

        int 확정건수 = service.reconcile(요청시각.plus(대사_대기).plusNanos(1_000));

        assertThat(확정건수).isZero();
        assertThat(gateway.requests()).extracting(PayoutOrder::payoutKey).containsExactly(지급.payoutKey());
        assertThat(payouts.findBySettlementId(정산서)).hasSize(1);
        assertThat(유일한_지급(정산서).getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.CONFIRMED);
    }

    @Test
    void 요청한_지_10분이_지나지_않은_지급은_결제사에_기록이_없어도_그대로_둔다() {
        Long 모름 = 확정(기사, 10_000);
        Long 요청중 = 확정(다른기사, 20_000);
        accounts.register(기사).register(다른기사);
        payouts.setNow(요청시각);
        Payout 모름_지급 = 결과를_모르는_지급(모름);
        요청만_기록된_지급(요청중);

        int 확정건수 = service.reconcile(요청시각.plus(대사_대기));

        assertThat(확정건수).isZero();
        assertThat(gateway.findCalls()).containsExactly(모름_지급.payoutKey());
        assertThat(유일한_지급(모름).getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(유일한_지급(요청중).getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(상태(모름)).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(상태(요청중)).isEqualTo(SettlementStatus.CONFIRMED);
    }

    @Test
    void 결제사_조회가_예외면_그_지급은_그대로_두고_나머지는_확정한다() {
        Long 조회실패 = 확정(기사, 10_000);
        Long 조회성공 = 확정(다른기사, 20_000);
        accounts.register(기사).register(다른기사);
        payouts.setNow(요청시각);
        Payout 조회실패_지급 = 결과를_모르는_지급(조회실패);
        Payout 조회성공_지급 = 결과를_모르는_지급(조회성공);
        gateway.willThrowOnFind(조회실패_지급.payoutKey(), new IllegalStateException("결제사 조회 장애"))
                .willFind(조회성공_지급.payoutKey(), new PayoutResult.Succeeded("pg-2"));

        int 확정건수 = service.reconcile(요청시각.plus(대사_대기).plusSeconds(60));

        assertThat(확정건수).isEqualTo(1);
        assertThat(유일한_지급(조회실패).getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(상태(조회실패)).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(유일한_지급(조회성공).getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(상태(조회성공)).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void 결제사가_조회에도_결과를_모른다고_답하면_오래된_지급이라도_실패로_만들지_않는다() {
        Long 정산서 = 확정(기사, 10_000);
        accounts.register(기사);
        payouts.setNow(요청시각);
        Payout 지급 = 결과를_모르는_지급(정산서);
        gateway.willFind(지급.payoutKey(), new PayoutResult.Unknown("은행 처리 중"));

        int 확정건수 = service.reconcile(요청시각.plus(Duration.ofHours(1)));

        assertThat(확정건수).isZero();
        assertThat(유일한_지급(정산서).getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.CONFIRMED);
    }

    @Test
    void 이미_끝난_지급에_결과를_다시_반영해도_그대로다() {
        Long 성공 = 확정(기사, 10_000);
        Long 실패 = 확정(다른기사, 20_000);
        accounts.register(기사).register(다른기사);
        gateway.willReturn(new PayoutResult.Succeeded("pg-ok"), new PayoutResult.Failed("한도 초과"));
        service.payDue();
        Long 성공_지급 = 유일한_지급(성공).getId();
        Long 실패_지급 = 유일한_지급(실패).getId();

        steps.complete(성공_지급, new PayoutResult.Failed("늦게 온 실패"));
        steps.complete(성공_지급, new PayoutResult.Unknown("늦게 온 모름"));
        steps.complete(성공_지급, new PayoutResult.Succeeded("pg-again"));
        steps.complete(실패_지급, new PayoutResult.Succeeded("pg-late"));
        steps.complete(실패_지급, new PayoutResult.Failed("늦게 온 다른 사유"));

        Payout 성공_후 = 유일한_지급(성공);
        assertThat(성공_후.getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(성공_후.getPgId()).isEqualTo("pg-ok");
        assertThat(성공_후.getFailReason()).isNull();
        assertThat(상태(성공)).isEqualTo(SettlementStatus.PAID);

        Payout 실패_후 = 유일한_지급(실패);
        assertThat(실패_후.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(실패_후.getPgId()).isNull();
        assertThat(실패_후.getFailReason()).isEqualTo("한도 초과");
        assertThat(상태(실패)).isEqualTo(SettlementStatus.FAILED);
    }

    @Test
    void 없는_지급에_결과를_반영하면_아무것도_하지_않는다() {
        steps.complete(404L, new PayoutResult.Succeeded("pg-ghost"));

        assertThat(payouts.count()).isZero();
        assertThat(settlements.count()).isZero();
    }

    @Test
    void 지급된_정산서와_금액_0_정산서는_지급_대상이_아니다() {
        Long 지급됨 = 정산서_저장(기사, SettlementStatus.PAID, 10_000).getId();
        Long 금액없음 = 정산서_저장(기사, SettlementStatus.CONFIRMED, 0).getId();
        Long 계좌없는_금액없음 = 정산서_저장(다른기사, SettlementStatus.CONFIRMED, 0).getId();
        accounts.register(기사);

        int 요청건수 = service.payDue();

        assertThat(요청건수).isZero();
        assertThat(steps.prepare(지급됨)).isEmpty();
        assertThat(steps.prepare(금액없음)).isEmpty();
        assertThat(steps.prepare(계좌없는_금액없음)).isEmpty();
        assertThat(상태(지급됨)).isEqualTo(SettlementStatus.PAID);
        assertThat(상태(금액없음)).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(상태(계좌없는_금액없음)).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(payouts.count()).isZero();
        assertThat(accounts.lookups()).isZero();
        assertThat(gateway.requestCount()).isZero();
    }

    @Test
    void 지급이_진행_중인_정산서는_다시_지급을_준비하지_않는다() {
        Long 정산서 = 확정(기사, 10_000);
        accounts.register(기사);

        Optional<PayoutOrder> 첫_준비 = steps.prepare(정산서);
        Optional<PayoutOrder> 다시_준비 = steps.prepare(정산서);

        assertThat(첫_준비).isPresent();
        assertThat(다시_준비).isEmpty();
        assertThat(payouts.findBySettlementId(정산서)).extracting(Payout::getStatus).containsExactly(PayoutStatus.REQUESTED);
        assertThat(상태(정산서)).isEqualTo(SettlementStatus.CONFIRMED);
    }

    @Test
    void 없는_정산서는_지급을_준비하지_않는다() {
        assertThat(steps.prepare(404L)).isEmpty();
        assertThat(payouts.count()).isZero();
    }

    @Test
    void 한_정산서에서_예외가_나도_나머지_정산서는_지급한다() {
        Long 문제 = 확정(기사, 10_000);
        Long 정상 = 확정(다른기사, 20_000);
        FakePayoutAccounts 장애_계좌 = new FakePayoutAccounts() {
            @Override
            public Optional<PayoutAccount> findByDriverId(Long taxiDriverId) {
                if(기사.equals(taxiDriverId)) throw new IllegalStateException("계좌 조회 장애");
                return super.findByDriverId(taxiDriverId);
            }
        };
        장애_계좌.register(기사).register(다른기사);
        PayoutService 서비스 = new PayoutService(settlements, payouts, gateway, new PayoutSteps(settlements, payouts, 장애_계좌));

        int 요청건수 = 서비스.payDue();

        assertThat(요청건수).isEqualTo(1);
        assertThat(payouts.findBySettlementId(문제)).isEmpty();
        assertThat(상태(문제)).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(상태(정상)).isEqualTo(SettlementStatus.PAID);
        assertThat(gateway.requests()).extracting(PayoutOrder::amount).containsExactly(20_000);
    }

    @Test
    void 한_번에_가져오는_수보다_지급할_정산서가_많아도_한_배치에서_모두_지급한다() {
        int 건수 = PayoutService.BATCH_SIZE + 1;
        for(long driverId = 1; driverId <= 건수; driverId++) {
            확정(driverId, 1_000);
            accounts.register(driverId);
        }

        int 요청건수 = service.payDue();

        assertThat(요청건수).isEqualTo(건수);
        assertThat(gateway.requestCount()).isEqualTo(건수);
        assertThat(settlements.findAll()).extracting(Settlement::getStatus).containsOnly(SettlementStatus.PAID);
    }

    @Test
    void 계좌가_없어_보류되는_정산서가_한_번에_가져오는_수만큼_앞에_있어도_뒤_정산서를_지급한다() {
        for(long driverId = 1; driverId <= PayoutService.BATCH_SIZE; driverId++) {
            확정(driverId, 1_000);
        }
        Long 계좌있는_기사 = PayoutService.BATCH_SIZE + 1L;
        Long 뒤 = 확정(계좌있는_기사, 5_000);
        accounts.register(계좌있는_기사);

        int 요청건수 = service.payDue();
        int 다음_요청건수 = service.payDue();

        assertThat(요청건수).isEqualTo(1);
        assertThat(다음_요청건수).isZero();
        assertThat(gateway.requests()).extracting(PayoutOrder::amount).containsExactly(5_000);
        assertThat(상태(뒤)).isEqualTo(SettlementStatus.PAID);
        assertThat(settlements.findAll()).filteredOn(s -> s.getStatus() == SettlementStatus.HOLD).hasSize(PayoutService.BATCH_SIZE);
    }

    @Test
    void 같은_배치에서_실패한_정산서가_한_번에_가져오는_수만큼_앞에_있어도_뒤_정산서를_지급하고_실패한_건은_다시_시도하지_않는다() {
        for(long driverId = 1; driverId <= PayoutService.BATCH_SIZE; driverId++) {
            확정(driverId, 1_000);
            accounts.register(driverId);
            gateway.willReturn(new PayoutResult.Failed("은행 점검"));
        }
        Long 뒤_기사 = PayoutService.BATCH_SIZE + 1L;
        Long 뒤 = 확정(뒤_기사, 5_000);
        accounts.register(뒤_기사);

        int 요청건수 = service.payDue();

        assertThat(요청건수).isEqualTo(PayoutService.BATCH_SIZE + 1);
        assertThat(gateway.requestCount()).isEqualTo(PayoutService.BATCH_SIZE + 1);
        assertThat(상태(뒤)).isEqualTo(SettlementStatus.PAID);
        assertThat(settlements.findAll()).filteredOn(s -> s.getStatus() == SettlementStatus.FAILED).hasSize(PayoutService.BATCH_SIZE);
        assertThat(payouts.findAll()).filteredOn(p -> p.getStatus() == PayoutStatus.FAILED).hasSize(PayoutService.BATCH_SIZE);
    }

    @Test
    void 결제사_호출_뒤_결과_반영이_실패해도_나머지는_지급하고_그_지급은_다시_요청하지_않고_대사가_확정한다() {
        Long 반영실패 = 확정(기사, 10_000);
        Long 정상 = 확정(다른기사, 20_000);
        accounts.register(기사).register(다른기사);
        payouts.setNow(요청시각);
        PayoutSteps 첫_반영만_실패하는_steps = new PayoutSteps(settlements, payouts, accounts) {
            private boolean 장애_냈음 = false;

            @Override
            public void complete(Long payoutId, PayoutResult result) {
                if(!장애_냈음) {
                    장애_냈음 = true;
                    throw new IllegalStateException("결과 저장 중 DB 장애");
                }
                super.complete(payoutId, result);
            }
        };
        PayoutService 서비스 = new PayoutService(settlements, payouts, gateway, 첫_반영만_실패하는_steps);

        int 요청건수 = 서비스.payDue();
        int 다음_요청건수 = 서비스.payDue();

        Payout 걸린_지급 = 유일한_지급(반영실패);
        assertThat(요청건수).isEqualTo(2);
        assertThat(다음_요청건수).isZero();
        assertThat(gateway.requestCount()).isEqualTo(2);
        assertThat(걸린_지급.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(상태(반영실패)).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(유일한_지급(정상).getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(상태(정상)).isEqualTo(SettlementStatus.PAID);

        gateway.willFind(걸린_지급.payoutKey(), new PayoutResult.Succeeded("pg-late"));
        int 확정건수 = 서비스.reconcile(요청시각.plus(대사_대기).plusSeconds(1));

        Payout 확정된 = 유일한_지급(반영실패);
        assertThat(확정건수).isEqualTo(1);
        assertThat(확정된.getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(확정된.getPgId()).isEqualTo("pg-late");
        assertThat(상태(반영실패)).isEqualTo(SettlementStatus.PAID);
        assertThat(gateway.requestCount()).isEqualTo(2);
    }

    @Test
    void 결과를_계속_모르는_지급이_한_번에_가져오는_수만큼_앞에_있어도_뒤_지급을_대사한다() {
        payouts.setNow(요청시각);
        for(long driverId = 1; driverId <= PayoutService.BATCH_SIZE; driverId++) {
            accounts.register(driverId);
            Payout 모름 = 결과를_모르는_지급(확정(driverId, 1_000));
            gateway.willFind(모름.payoutKey(), new PayoutResult.Unknown("은행 처리 중"));
        }
        Long 뒤_기사 = PayoutService.BATCH_SIZE + 1L;
        accounts.register(뒤_기사);
        Long 뒤 = 확정(뒤_기사, 5_000);
        Payout 뒤_지급 = 결과를_모르는_지급(뒤);
        gateway.willFind(뒤_지급.payoutKey(), new PayoutResult.Succeeded("pg-tail"));

        int 확정건수 = service.reconcile(요청시각.plus(대사_대기).plusSeconds(1));

        assertThat(확정건수).isEqualTo(1);
        assertThat(gateway.findCalls()).hasSize(PayoutService.BATCH_SIZE + 1).doesNotHaveDuplicates();
        assertThat(유일한_지급(뒤).getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(상태(뒤)).isEqualTo(SettlementStatus.PAID);
        assertThat(payouts.findAll()).filteredOn(p -> p.getStatus() == PayoutStatus.UNKNOWN).hasSize(PayoutService.BATCH_SIZE);
    }

    @Test
    void 지급은_매일_KST_10시에_잠금을_잡고_돌고_대사는_10분마다_돈다() throws Exception {
        Method pay = PayoutScheduler.class.getDeclaredMethod("pay");
        Scheduled 지급 = pay.getAnnotation(Scheduled.class);
        Scheduled 대사 = PayoutScheduler.class.getDeclaredMethod("reconcile").getAnnotation(Scheduled.class);
        ZonedDateTime 수요일_정오 = ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, SettlementPeriod.ZONE);

        assertThat(지급.cron()).isEqualTo("0 0 10 * * *");
        assertThat(지급.zone()).isEqualTo("Asia/Seoul");
        assertThat(pay.getAnnotation(SchedulerLock.class).name()).isEqualTo("settlement-payout");
        assertThat(CronExpression.parse(지급.cron()).next(수요일_정오).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 10, 29, 10, 0));
        assertThat(Duration.ofMillis(대사.fixedDelay())).isEqualTo(Duration.ofMinutes(10));
    }

    private Long 확정(Long taxiDriverId, int amount) {
        return 정산서_저장(taxiDriverId, SettlementStatus.CONFIRMED, amount).getId();
    }

    private Settlement 정산서_저장(Long taxiDriverId, SettlementStatus status, int amount) {
        SettlementPeriod period = SettlementPeriod.weekOf(첫_주.plusWeeks(주_순번++));
        return settlements.save(Settlement.load(null, taxiDriverId, period.start(), period.end(), amount, status, null, null));
    }

    private Payout 요청만_기록된_지급(Long settlementId) {
        PayoutOrder order = steps.prepare(settlementId).orElseThrow();
        return payouts.findById(order.payoutId()).orElseThrow();
    }

    private Payout 결과를_모르는_지급(Long settlementId) {
        PayoutOrder order = steps.prepare(settlementId).orElseThrow();
        steps.complete(order.payoutId(), new PayoutResult.Unknown("응답 시간 초과"));
        return payouts.findById(order.payoutId()).orElseThrow();
    }

    private Payout 유일한_지급(Long settlementId) {
        List<Payout> found = payouts.findBySettlementId(settlementId);
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    private SettlementStatus 상태(Long settlementId) {
        return settlements.findById(settlementId).orElseThrow().getStatus();
    }
}
