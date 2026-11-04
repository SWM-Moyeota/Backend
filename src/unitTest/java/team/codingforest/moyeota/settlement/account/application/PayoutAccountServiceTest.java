package team.codingforest.moyeota.settlement.account.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.account.domain.BusinessInfo;
import team.codingforest.moyeota.settlement.account.domain.BusinessType;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccount;
import team.codingforest.moyeota.settlement.account.domain.SellerGatewayException;
import team.codingforest.moyeota.settlement.account.domain.SellerRegistration;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;
import team.codingforest.moyeota.settlement.account.dto.PayoutAccountRegisterCommand;
import team.codingforest.moyeota.settlement.account.dto.PayoutAccountResponse;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutAccountServiceTest {
    private static final Long 기사 = 9L;
    private static final Long 다른기사 = 10L;
    private static final BusinessInfo 사업자 = new BusinessInfo("모여타택시", "홍길동", "2208112341", "owner@example.com", "0212345678");
    private static final String 계좌번호 = "110123456789";
    private static final String 예금주 = "모여타택시";
    private static final PayoutAccountRegisterCommand 등록_요청 =
            new PayoutAccountRegisterCommand(BusinessType.CORPORATE, 사업자, BankCode.KOOKMIN, 계좌번호, 예금주);

    private FakeDriverPayoutAccounts accounts;
    private FakeSellerGateway gateway;
    private PayoutAccountService service;

    @BeforeEach
    void setUp() {
        accounts = new FakeDriverPayoutAccounts();
        gateway = new FakeSellerGateway();
        service = new PayoutAccountService(accounts, gateway);
    }

    // ---------- register ----------

    @Test
    void 처음_등록하면_행을_먼저_저장해_만든_refSellerId_와_계좌번호_원문을_토스에_보내고_셀러를_채운다() {
        PayoutAccountResponse response = service.register(기사, 등록_요청);

        SellerRegistration sent = gateway.registrations().getFirst();
        assertThat(sent.refSellerId()).isEqualTo("pa00031");
        assertThat(sent.businessType()).isEqualTo(BusinessType.CORPORATE);
        assertThat(sent.businessInfo()).isEqualTo(사업자);
        assertThat(sent.bankCode()).isEqualTo(BankCode.KOOKMIN);
        assertThat(sent.accountNumber()).isEqualTo(계좌번호);
        assertThat(sent.accountHolder()).isEqualTo(예금주);

        DriverPayoutAccount saved = accounts.row(31L).orElseThrow();
        assertThat(saved.getSellerId()).isEqualTo("seller_pa00031");
        assertThat(saved.getSellerStatus()).isEqualTo(SellerStatus.PARTIALLY_APPROVED);
        assertThat(saved.getMaskedAccountNumber()).isEqualTo("********6789");

        assertThat(response.businessType()).isEqualTo(BusinessType.CORPORATE);
        assertThat(response.bankCode()).isEqualTo(BankCode.KOOKMIN);
        assertThat(response.bankName()).isEqualTo("국민은행");
        assertThat(response.maskedAccountNumber()).isEqualTo("********6789");
        assertThat(response.accountHolder()).isEqualTo(예금주);
        assertThat(response.sellerStatus()).isEqualTo(SellerStatus.PARTIALLY_APPROVED);
        assertThat(response.payable()).isTrue();
    }

    @Test
    void 토스가_본인인증을_요구하면_등록은_되지만_아직_지급할_수_없다() {
        gateway.respondWith(SellerStatus.APPROVAL_REQUIRED);

        PayoutAccountResponse response = service.register(기사, 등록_요청);

        assertThat(response.sellerStatus()).isEqualTo(SellerStatus.APPROVAL_REQUIRED);
        assertThat(response.payable()).isFalse();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "REJECTED, PAYOUT_ACCOUNT_REJECTED",
            "LOCKED, PAYOUT_ACCOUNT_LOCKED",
            "UNAVAILABLE, PAYOUT_PROVIDER_UNAVAILABLE"
    })
    void 토스_등록이_실패하면_이유와_상관없이_행을_지우고_이유에_맞는_에러로_바꾼다(SellerGatewayException.Reason reason, SettlementErrorCode errorCode) {
        gateway.failWith(reason);

        assertErrorCode(() -> service.register(기사, 등록_요청), errorCode);

        assertThat(accounts.size()).isZero();
    }

    @Test
    void 토스_등록이_실패한_뒤_바로_다시_등록할_수_있다() {
        gateway.failWith(SellerGatewayException.Reason.UNAVAILABLE);
        assertErrorCode(() -> service.register(기사, 등록_요청), SettlementErrorCode.PAYOUT_PROVIDER_UNAVAILABLE);

        FakeSellerGateway recovered = new FakeSellerGateway();
        PayoutAccountResponse response = new PayoutAccountService(accounts, recovered).register(기사, 등록_요청);

        assertThat(response.sellerStatus()).isEqualTo(SellerStatus.PARTIALLY_APPROVED);
        assertThat(recovered.registrations().getFirst().refSellerId()).isEqualTo("pa00032");
    }

    @Test
    void 이미_셀러가_등록된_기사는_다시_등록할_수_없고_토스를_부르지_않는다() {
        accounts.put(등록된_계좌(31L, 기사, SellerStatus.APPROVED));

        assertErrorCode(() -> service.register(기사, 등록_요청), SettlementErrorCode.PAYOUT_ACCOUNT_ALREADY_REGISTERED);

        assertThat(gateway.untouched()).isTrue();
        assertThat(accounts.row(31L)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(longs = {2, 13, 50})
    void 셀러_없는_행이_1분이_안_됐으면_다른_요청이_등록_중이라_지우지_않고_토스도_부르지_않는다(long secondsAgo) {
        accounts.put(셀러_없는_계좌(31L, 기사, Instant.now().minusSeconds(secondsAgo)));

        assertErrorCode(() -> service.register(기사, 등록_요청), SettlementErrorCode.PAYOUT_ACCOUNT_REGISTERING);

        assertThat(gateway.untouched()).isTrue();
        assertThat(accounts.row(31L)).isPresent();
    }

    @Test
    void 셀러_없는_행이_1분이_지났으면_버려진_행이라_지우고_새_refSellerId_로_등록한다() {
        accounts.put(셀러_없는_계좌(31L, 기사, Instant.now().minusSeconds(120)));

        service.register(기사, 등록_요청);

        assertThat(accounts.row(31L)).isEmpty();
        assertThat(accounts.row(32L)).get().extracting(DriverPayoutAccount::getSellerId).isEqualTo("seller_pa00032");
        assertThat(gateway.registrations().getFirst().refSellerId()).isEqualTo("pa00032");
    }

    @Test
    void 조회와_저장_사이에_같은_기사의_행이_먼저_들어가면_UNIQUE_에_걸려_이미_등록으로_답하고_토스를_부르지_않는다() {
        accounts.insertedByAnotherRequestAfterNextLookup(셀러_없는_계좌(31L, 기사, Instant.now()));

        assertErrorCode(() -> service.register(기사, 등록_요청), SettlementErrorCode.PAYOUT_ACCOUNT_ALREADY_REGISTERED);

        assertThat(gateway.untouched()).isTrue();
        assertThat(accounts.row(31L)).isPresent();
    }

    @Test
    void 계좌번호가_잘못되면_저장도_토스_호출도_하지_않는다() {
        PayoutAccountRegisterCommand 잘못된_요청 =
                new PayoutAccountRegisterCommand(BusinessType.CORPORATE, 사업자, BankCode.KOOKMIN, "110-1234", 예금주);

        assertErrorCode(() -> service.register(기사, 잘못된_요청), SettlementErrorCode.INVALID_ACCOUNT_NUMBER);

        assertThat(accounts.size()).isZero();
        assertThat(gateway.untouched()).isTrue();
    }

    @Test
    void 다른_기사의_계좌는_등록에_영향을_주지_않는다() {
        accounts.put(등록된_계좌(31L, 다른기사, SellerStatus.APPROVED));

        service.register(기사, 등록_요청);

        assertThat(accounts.size()).isEqualTo(2);
        assertThat(gateway.registrations().getFirst().refSellerId()).isEqualTo("pa00032");
    }

    // ---------- changeAccount ----------

    @Test
    void 계좌를_바꾸면_토스_셀러_계좌를_원문으로_바꾸고_토스가_준_상태로_덮어_저장한다() {
        accounts.put(등록된_계좌(31L, 기사, SellerStatus.APPROVED));
        gateway.respondWith(SellerStatus.APPROVAL_REQUIRED);

        PayoutAccountResponse response = service.changeAccount(기사, BankCode.SHINHAN, "1002003004005", "새예금주");

        assertThat(gateway.accountChanges()).containsExactly(
                new FakeSellerGateway.AccountChange("seller_31", BankCode.SHINHAN, "1002003004005", "새예금주"));

        DriverPayoutAccount saved = accounts.row(31L).orElseThrow();
        assertThat(saved.getBankCode()).isEqualTo(BankCode.SHINHAN);
        assertThat(saved.getMaskedAccountNumber()).isEqualTo("*********4005");
        assertThat(saved.getAccountHolder()).isEqualTo("새예금주");
        assertThat(saved.getSellerStatus()).isEqualTo(SellerStatus.APPROVAL_REQUIRED);

        assertThat(response.bankName()).isEqualTo("신한은행");
        assertThat(response.payable()).isFalse();
    }

    @Test
    void 지급_계좌가_없으면_바꿀_수_없고_토스를_부르지_않는다() {
        assertErrorCode(() -> service.changeAccount(기사, BankCode.SHINHAN, "1002003004005", "새예금주"), SettlementErrorCode.PAYOUT_ACCOUNT_NOT_FOUND);

        assertThat(gateway.untouched()).isTrue();
    }

    @Test
    void 셀러_등록이_끝나지_않은_계좌는_바꿀_수_없고_토스를_부르지_않는다() {
        accounts.put(셀러_없는_계좌(31L, 기사, Instant.now().minusSeconds(120)));

        assertErrorCode(() -> service.changeAccount(기사, BankCode.SHINHAN, "1002003004005", "새예금주"), SettlementErrorCode.PAYOUT_ACCOUNT_NOT_FOUND);

        assertThat(gateway.untouched()).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "REJECTED, PAYOUT_ACCOUNT_REJECTED",
            "LOCKED, PAYOUT_ACCOUNT_LOCKED",
            "UNAVAILABLE, PAYOUT_PROVIDER_UNAVAILABLE"
    })
    void 토스_계좌_변경이_실패하면_에러로_바꾸고_우리_계좌는_그대로다(SellerGatewayException.Reason reason, SettlementErrorCode errorCode) {
        accounts.put(등록된_계좌(31L, 기사, SellerStatus.APPROVED));
        gateway.failWith(reason);

        assertErrorCode(() -> service.changeAccount(기사, BankCode.SHINHAN, "1002003004005", "새예금주"), errorCode);

        DriverPayoutAccount kept = accounts.row(31L).orElseThrow();
        assertThat(kept.getBankCode()).isEqualTo(BankCode.KOOKMIN);
        assertThat(kept.getMaskedAccountNumber()).isEqualTo("********6789");
        assertThat(kept.getSellerStatus()).isEqualTo(SellerStatus.APPROVED);
    }

    @Test
    void 바꿀_계좌번호가_잘못되면_토스를_부르지_않는다() {
        accounts.put(등록된_계좌(31L, 기사, SellerStatus.APPROVED));

        assertErrorCode(() -> service.changeAccount(기사, BankCode.SHINHAN, "1002-003", "새예금주"), SettlementErrorCode.INVALID_ACCOUNT_NUMBER);

        assertThat(gateway.untouched()).isTrue();
    }

    // ---------- find ----------

    @Test
    void 지급_계좌가_없으면_조회할_수_없다() {
        assertErrorCode(() -> service.find(기사), SettlementErrorCode.PAYOUT_ACCOUNT_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, names = {"PARTIALLY_APPROVED", "APPROVED"})
    void 승인된_셀러는_저장된_상태를_주고_토스를_부르지_않는다(SellerStatus status) {
        accounts.put(등록된_계좌(31L, 기사, status));

        PayoutAccountResponse response = service.find(기사);

        assertThat(response.sellerStatus()).isEqualTo(status);
        assertThat(response.payable()).isTrue();
        assertThat(gateway.untouched()).isTrue();
    }

    @Test
    void 셀러_등록이_끝나지_않은_행은_상태_없이_주고_토스를_부르지_않는다() {
        accounts.put(셀러_없는_계좌(31L, 기사, Instant.now()));

        PayoutAccountResponse response = service.find(기사);

        assertThat(response.sellerStatus()).isNull();
        assertThat(response.payable()).isFalse();
        assertThat(gateway.untouched()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, names = {"APPROVAL_REQUIRED", "KYC_REQUIRED"})
    void 승인_대기_셀러는_토스에서_상태를_다시_읽어_바뀌었으면_저장한다(SellerStatus pending) {
        accounts.put(등록된_계좌(31L, 기사, pending));
        gateway.respondWith(SellerStatus.APPROVED);

        PayoutAccountResponse response = service.find(기사);

        assertThat(gateway.finds()).containsExactly("seller_31");
        assertThat(response.sellerStatus()).isEqualTo(SellerStatus.APPROVED);
        assertThat(response.payable()).isTrue();
        assertThat(accounts.row(31L)).get().extracting(DriverPayoutAccount::getSellerStatus).isEqualTo(SellerStatus.APPROVED);
    }

    @Test
    void 승인_대기_셀러의_상태가_그대로면_저장하지_않는다() {
        DriverPayoutAccount pending = 등록된_계좌(31L, 기사, SellerStatus.APPROVAL_REQUIRED);
        accounts.put(pending);
        gateway.respondWith(SellerStatus.APPROVAL_REQUIRED);

        PayoutAccountResponse response = service.find(기사);

        assertThat(response.sellerStatus()).isEqualTo(SellerStatus.APPROVAL_REQUIRED);
        assertThat(accounts.row(31L)).get().extracting(DriverPayoutAccount::getUpdatedAt).isEqualTo(pending.getUpdatedAt());
    }

    @ParameterizedTest
    @EnumSource(SellerGatewayException.Reason.class)
    void 승인_대기_셀러의_상태를_토스에서_못_읽으면_조회를_실패시키지_않고_저장된_상태를_준다(SellerGatewayException.Reason reason) {
        accounts.put(등록된_계좌(31L, 기사, SellerStatus.KYC_REQUIRED));
        gateway.failWith(reason);

        PayoutAccountResponse response = service.find(기사);

        assertThat(response.sellerStatus()).isEqualTo(SellerStatus.KYC_REQUIRED);
        assertThat(response.payable()).isFalse();
    }

    private static DriverPayoutAccount 셀러_없는_계좌(Long id, Long driverId, Instant createdAt) {
        return DriverPayoutAccount.load(id, driverId, BusinessType.CORPORATE, BankCode.KOOKMIN, "********6789", 예금주,
                null, null, createdAt, createdAt);
    }

    private static DriverPayoutAccount 등록된_계좌(Long id, Long driverId, SellerStatus status) {
        Instant createdAt = Instant.parse("2026-11-03T01:00:00Z");
        return DriverPayoutAccount.load(id, driverId, BusinessType.CORPORATE, BankCode.KOOKMIN, "********6789", 예금주,
                "seller_" + id, status, createdAt, createdAt);
    }

    private static void assertErrorCode(Runnable action, SettlementErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
