package team.codingforest.moyeota.settlement.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import team.codingforest.moyeota.common.exception.GlobalExceptionHandler;
import team.codingforest.moyeota.driver.api.CurrentDriver;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettlementControllerTest {
    private static final Long 기사 = 9L;
    private static final Long 다른기사 = 10L;

    private FakePayouts payouts;
    private FakeSettlements settlements;
    private FakeSettlementLedgers ledgers;
    private SettlementCloseService closeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        payouts = new FakePayouts();
        settlements = new FakeSettlements();
        ledgers = new FakeSettlementLedgers(settlements);
        closeService = new SettlementCloseService(settlements, ledgers);

        mvc = MockMvcBuilders.standaloneSetup(new SettlementController(new SettlementQueryService(settlements, ledgers, payouts)))
                .setCustomArgumentResolvers(new StubCurrentDriverResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 토큰의_기사가_고른_날짜가_속한_주의_정산서를_월요일부터_일요일까지_7칸으로_내려준다() throws Exception {
        운행(기사, 1001L, 15000, 0, kst("2026-10-19T13:00"));
        운행(기사, 1002L, 14000, 1000, kst("2026-10-21T08:00"));
        운행(기사, 1003L, 14000, 1000, kst("2026-10-21T10:00"));
        운행(다른기사, 1004L, 88000, 0, kst("2026-10-21T11:00"));
        Settlement 지난_주 = 정산서_생성(기사, LocalDate.of(2026, 10, 21));
        정산서_생성(다른기사, LocalDate.of(2026, 10, 21));

        mvc.perform(get("/api/v1/drivers/me/settlements").param("date", "2026-10-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.weekEnd").value("2026-10-25"))
                .andExpect(jsonPath("$.settlementId").value(지난_주.getId()))
                .andExpect(jsonPath("$.amount").value(45000))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.taxiDriverId").doesNotExist())
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.days[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.days[0].amount").value(15000))
                .andExpect(jsonPath("$.days[0].rideCount").value(1))
                .andExpect(jsonPath("$.days[1].date").value("2026-10-20"))
                .andExpect(jsonPath("$.days[1].amount").value(0))
                .andExpect(jsonPath("$.days[1].rideCount").value(0))
                .andExpect(jsonPath("$.days[2].date").value("2026-10-21"))
                .andExpect(jsonPath("$.days[2].amount").value(30000))
                .andExpect(jsonPath("$.days[2].rideCount").value(2))
                .andExpect(jsonPath("$.days[6].date").value("2026-10-25"))
                .andExpect(jsonPath("$.days[6].amount").value(0));
    }

    @Test
    void date_를_생략하면_오늘_KST_가_속한_주의_미정산_운행으로_예정_금액을_주고_정산서_ID_와_상태는_null_이다() throws Exception {
        SettlementPeriod 이번_주 = SettlementPeriod.weekOf(Instant.now());
        운행(기사, 1001L, 12000, 0, 이번_주.start());
        운행(다른기사, 1002L, 50000, 0, 이번_주.start());

        mvc.perform(get("/api/v1/drivers/me/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value(이번_주.startDate().toString()))
                .andExpect(jsonPath("$.weekEnd").value(이번_주.lastDate().toString()))
                .andExpect(jsonPath("$.settlementId").value(nullValue()))
                .andExpect(jsonPath("$.status").value(nullValue()))
                .andExpect(jsonPath("$.amount").value(12000))
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.days[0].amount").value(12000));
    }

    @Test
    void date_가_빈_값이면_오늘_KST_가_속한_주를_준다() throws Exception {
        LocalDate 오늘 = LocalDate.now(SettlementPeriod.ZONE);

        mvc.perform(get("/api/v1/drivers/me/settlements").param("date", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value(SettlementPeriod.weekOf(오늘).startDate().toString()))
                .andExpect(jsonPath("$.days", hasSize(7)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-19", "2026-10-21", "2026-10-25"})
    void 그_주의_어느_날짜로_조회해도_같은_주를_준다(String date) throws Exception {
        운행(기사, 1001L, 12000, 0, kst("2026-10-21T09:00"));
        Settlement 지난_주 = 정산서_생성(기사, LocalDate.of(2026, 10, 21));

        mvc.perform(get("/api/v1/drivers/me/settlements").param("date", date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.weekEnd").value("2026-10-25"))
                .andExpect(jsonPath("$.settlementId").value(지난_주.getId()));
    }

    @Test
    void 정산서가_아직_없는_지난_주는_그_주_미정산_운행으로_예정_금액을_주고_정산서_ID_와_상태는_null_이다() throws Exception {
        운행(기사, 1001L, 9000, 0, kst("2026-03-04T10:00"));
        운행(기사, 1002L, 7000, 0, kst("2026-03-09T10:00"));

        mvc.perform(get("/api/v1/drivers/me/settlements").param("date", "2026-03-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-03-02"))
                .andExpect(jsonPath("$.weekEnd").value("2026-03-08"))
                .andExpect(jsonPath("$.settlementId").value(nullValue()))
                .andExpect(jsonPath("$.status").value(nullValue()))
                .andExpect(jsonPath("$.amount").value(9000))
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.days[0].date").value("2026-03-02"))
                .andExpect(jsonPath("$.days[2].date").value("2026-03-04"))
                .andExpect(jsonPath("$.days[2].amount").value(9000))
                .andExpect(jsonPath("$.days[2].rideCount").value(1))
                .andExpect(jsonPath("$.days[6].date").value("2026-03-08"));
    }

    @Test
    void 운행이_없는_주는_정산서_ID_와_상태가_null_이고_0원에_7칸_모두_0이다() throws Exception {
        운행(기사, 1001L, 9000, 0, kst("2026-03-04T10:00"));

        mvc.perform(get("/api/v1/drivers/me/settlements").param("date", "2026-03-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-03-09"))
                .andExpect(jsonPath("$.weekEnd").value("2026-03-15"))
                .andExpect(jsonPath("$.settlementId").value(nullValue()))
                .andExpect(jsonPath("$.status").value(nullValue()))
                .andExpect(jsonPath("$.amount").value(0))
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.days[0].date").value("2026-03-09"))
                .andExpect(jsonPath("$.days[6].date").value("2026-03-15"))
                .andExpect(jsonPath("$.days[*].amount", everyItem(is(0))))
                .andExpect(jsonPath("$.days[*].rideCount", everyItem(is(0))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-01", "2026-02-30", "2026/10/28", "20261028", "28-10-2026", "2026-10", "abc"})
    void date_형식이_틀리면_400_INVALID_REQUEST(String date) throws Exception {
        mvc.perform(get("/api/v1/drivers/me/settlements").param("date", date))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("date: 형식이 올바르지 않습니다."));
    }

    @Test
    void 정산서_상세는_원장을_운행_종료_시각_순으로_주고_최근_지급의_가린_계좌번호만_내려준다() throws Exception {
        Instant 저녁운행 = kst("2026-11-01T21:00:00.123456");
        Instant 아침운행 = kst("2026-10-28T09:30");
        운행(기사, 1002L, 15000, 1000, 저녁운행);
        운행(기사, 1001L, 12000, 500, 아침운행);
        Settlement 정산서 = 정산서_생성(기사, LocalDate.of(2026, 10, 28));
        지급(정산서, PayoutStatus.FAILED, "예금주 불일치", null, kst("2026-11-02T10:00"));
        Instant 요청시각 = kst("2026-11-03T10:00:00.654321");
        지급(정산서, PayoutStatus.SUCCEED, null, "pg-거래-777", 요청시각);

        String body = mvc.perform(get("/api/v1/drivers/me/settlements/{settlementId}", 정산서.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value(정산서.getId()))
                .andExpect(jsonPath("$.date").value("2026-10-26"))
                .andExpect(jsonPath("$.periodStart").value("2026-10-25T15:00:00Z"))
                .andExpect(jsonPath("$.periodEnd").value("2026-11-01T15:00:00Z"))
                .andExpect(jsonPath("$.amount").value(28500))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.taxiDriverId").doesNotExist())
                .andExpect(jsonPath("$.rides", hasSize(2)))
                .andExpect(jsonPath("$.rides[0].partyId").value(1001L))
                .andExpect(jsonPath("$.rides[0].totalFare").value(12000))
                .andExpect(jsonPath("$.rides[0].platformPromotion").value(500))
                .andExpect(jsonPath("$.rides[0].settlementAmount").value(12500))
                .andExpect(jsonPath("$.rides[0].occurredAt").value(아침운행.toString()))
                .andExpect(jsonPath("$.rides[1].partyId").value(1002L))
                .andExpect(jsonPath("$.rides[1].settlementAmount").value(16000))
                .andExpect(jsonPath("$.rides[1].occurredAt").value(저녁운행.toString()))
                .andExpect(jsonPath("$.payout.status").value("SUCCEED"))
                .andExpect(jsonPath("$.payout.amount").value(28500))
                .andExpect(jsonPath("$.payout.bankCode").exists())
                .andExpect(jsonPath("$.payout.maskedAccountNumber").value("********6789"))
                .andExpect(jsonPath("$.payout.failReason").value(nullValue()))
                .andExpect(jsonPath("$.payout.requestedAt").value(요청시각.toString()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).as("예금주·전체 계좌번호·결제사 거래 ID 와 지난 지급 시도는 내려가지 않는다")
                .doesNotContain(FakePayoutAccounts.SAMPLE_ACCOUNT_NUMBER, FakePayoutAccounts.SAMPLE_ACCOUNT_HOLDER, "pg-거래-777", "예금주 불일치",
                        "\"accountNumber\"", "\"accountHolder\"", "\"pgId\"");
    }

    @Test
    void 지급을_시도한_적이_없으면_payout_은_null_이다() throws Exception {
        운행(기사, 1001L, 12000, 0, kst("2026-10-28T09:30"));
        Settlement 정산서 = 정산서_생성(기사, LocalDate.of(2026, 10, 28));

        mvc.perform(get("/api/v1/drivers/me/settlements/{settlementId}", 정산서.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.rides", hasSize(1)))
                .andExpect(jsonPath("$.payout").value(nullValue()));
    }

    @Test
    void 다른_기사의_정산서_상세는_404_SETTLEMENT_NOT_FOUND() throws Exception {
        운행(다른기사, 1001L, 77000, 0, kst("2026-10-28T09:30"));
        Settlement 남의_정산서 = 정산서_생성(다른기사, LocalDate.of(2026, 10, 28));

        mvc.perform(get("/api/v1/drivers/me/settlements/{settlementId}", 남의_정산서.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 정산서입니다."))
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath("$.rides").doesNotExist());
    }

    @Test
    void 없는_정산서_상세는_404_SETTLEMENT_NOT_FOUND() throws Exception {
        mvc.perform(get("/api/v1/drivers/me/settlements/{settlementId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    void settlementId_가_정수가_아니면_400_INVALID_REQUEST() throws Exception {
        mvc.perform(get("/api/v1/drivers/me/settlements/{settlementId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("settlementId: 형식이 올바르지 않습니다."));
    }

    private void 운행(Long taxiDriverId, Long partyId, int totalFare, int platformPromotion, Instant occurredAt) {
        ledgers.save(SettlementLedger.record(taxiDriverId, partyId, totalFare, platformPromotion, occurredAt));
    }

    private Settlement 정산서_생성(Long taxiDriverId, LocalDate date) {
        SettlementPeriod week = SettlementPeriod.weekOf(date);
        closeService.close(taxiDriverId, week);
        return settlements.findByDriverAndPeriod(taxiDriverId, week).orElseThrow();
    }

    private void 지급(Settlement settlement, PayoutStatus status, String failReason, String pgId, Instant requestedAt) {
        payouts.setNow(requestedAt);
        payouts.save(Payout.load(null, settlement.getId(), settlement.getAmount(), status, pgId, FakePayoutAccounts.SAMPLE_BANK,
                FakePayoutAccounts.SAMPLE_ACCOUNT_NUMBER, FakePayoutAccounts.SAMPLE_ACCOUNT_HOLDER, failReason, null, null));
    }

    private static Instant kst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SettlementPeriod.ZONE).toInstant();
    }

    static class StubCurrentDriverResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentDriver.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                      NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return 기사;
        }
    }
}
