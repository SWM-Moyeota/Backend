package team.codingforest.moyeota.settlement.account.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

class DriverPayoutAccountTest {
    private static final Long 기사 = 9L;
    private static final Long 계좌_번호 = 31L;
    private static final BankCode 은행 = BankCode.KOOKMIN;
    private static final String 계좌번호 = "110123456789";
    private static final String 예금주 = "모여타택시";
    private static final String 셀러 = "seller_abc";
    private static final Instant 생성 = Instant.parse("2026-11-03T01:00:00Z");

    @Test
    void 지급_계좌를_만들면_계좌번호는_뒤_4자리만_남기고_셀러는_비어_있다() {
        DriverPayoutAccount account = DriverPayoutAccount.create(기사, BusinessType.CORPORATE, 은행, 계좌번호, 예금주);

        assertThat(account.getId()).isNull();
        assertThat(account.getTaxiDriverId()).isEqualTo(기사);
        assertThat(account.getBusinessType()).isEqualTo(BusinessType.CORPORATE);
        assertThat(account.getBankCode()).isEqualTo(은행);
        assertThat(account.getMaskedAccountNumber()).isEqualTo("********6789");
        assertThat(account.getAccountHolder()).isEqualTo(예금주);
        assertThat(account.getSellerId()).isNull();
        assertThat(account.getSellerStatus()).isNull();
        assertThat(account.isSellerRegistered()).isFalse();
        assertThat(account.canReceivePayout()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "1, *",
            "1234, ****",
            "12345, *2345",
            "12345678901234, **********1234"
    })
    void 계좌번호가_4자리_이하면_모두_가리고_길면_뒤_4자리만_남긴다(String accountNumber, String masked) {
        DriverPayoutAccount account = DriverPayoutAccount.create(기사, BusinessType.CORPORATE, 은행, accountNumber, 예금주);

        assertThat(account.getMaskedAccountNumber()).isEqualTo(masked);
    }

    @Test
    void 기사나_사업자_유형이_없으면_만들_수_없다() {
        assertErrorCode(() -> DriverPayoutAccount.create(null, BusinessType.CORPORATE, 은행, 계좌번호, 예금주), SettlementErrorCode.INVALID_PAYOUT_ACCOUNT);
        assertErrorCode(() -> DriverPayoutAccount.create(기사, null, 은행, 계좌번호, 예금주), SettlementErrorCode.INVALID_PAYOUT_ACCOUNT);
    }

    @Test
    void 은행이_없으면_만들_수_없다() {
        assertErrorCode(() -> DriverPayoutAccount.create(기사, BusinessType.CORPORATE, null, 계좌번호, 예금주), SettlementErrorCode.INVALID_PAYOUT_ACCOUNT);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"110-123-456789", "12345678901 ", "123456789012345", "abc1234"})
    void 계좌번호가_숫자만_14자리_이하가_아니면_만들_수_없다(String accountNumber) {
        assertErrorCode(() -> DriverPayoutAccount.create(기사, BusinessType.CORPORATE, 은행, accountNumber, 예금주), SettlementErrorCode.INVALID_ACCOUNT_NUMBER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 예금주가_비어_있으면_만들_수_없다(String accountHolder) {
        assertErrorCode(() -> DriverPayoutAccount.create(기사, BusinessType.CORPORATE, 은행, 계좌번호, accountHolder), SettlementErrorCode.INVALID_ACCOUNT_HOLDER);
    }

    @Test
    void 예금주는_50자까지_되고_51자부터는_만들_수_없다() {
        DriverPayoutAccount account = DriverPayoutAccount.create(기사, BusinessType.CORPORATE, 은행, 계좌번호, "가".repeat(50));

        assertThat(account.getAccountHolder()).hasSize(50);
        assertErrorCode(() -> DriverPayoutAccount.create(기사, BusinessType.CORPORATE, 은행, 계좌번호, "가".repeat(51)), SettlementErrorCode.INVALID_ACCOUNT_HOLDER);
    }

    @Test
    void 저장된_계좌의_셀러_참조_id_는_접두사와_다섯_자리로_채운_계좌_번호로_만든다() {
        assertThat(저장된_계좌().refSellerId()).isEqualTo("pa00031");
    }

    @ParameterizedTest
    @CsvSource({
            "1, pa00001",
            "99999, pa99999",
            "123456, pa123456"
    })
    void 셀러_참조_id_는_토스_최소_길이_7자를_넘기고_계좌_번호가_크면_그대로_길어진다(Long id, String expected) {
        DriverPayoutAccount account = DriverPayoutAccount.load(id, 기사, BusinessType.CORPORATE, 은행, "********6789", 예금주,
                null, null, 생성, null);

        assertThat(account.refSellerId()).isEqualTo(expected).hasSizeBetween(7, 20);
    }

    @Test
    void 저장되지_않은_계좌에는_셀러_참조_id_가_없다() {
        DriverPayoutAccount account = DriverPayoutAccount.create(기사, BusinessType.CORPORATE, 은행, 계좌번호, 예금주);

        assertThatThrownBy(account::refSellerId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 셀러_참조_id_가_20자를_넘으면_막는다() {
        DriverPayoutAccount account = DriverPayoutAccount.load(Long.MAX_VALUE, 기사, BusinessType.CORPORATE, 은행, "********6789", 예금주,
                null, null, 생성, null);

        assertThatThrownBy(account::refSellerId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 토스_셀러를_등록하면_셀러_id_와_상태가_채워진다() {
        DriverPayoutAccount account = 저장된_계좌();

        account.registerSeller(셀러, SellerStatus.PARTIALLY_APPROVED);

        assertThat(account.getSellerId()).isEqualTo(셀러);
        assertThat(account.getSellerStatus()).isEqualTo(SellerStatus.PARTIALLY_APPROVED);
        assertThat(account.isSellerRegistered()).isTrue();
    }

    @Test
    void 이미_셀러가_등록된_계좌는_다시_등록할_수_없다() {
        DriverPayoutAccount account = 등록된_계좌(SellerStatus.PARTIALLY_APPROVED);

        assertErrorCode(() -> account.registerSeller("seller_other", SellerStatus.PARTIALLY_APPROVED), SettlementErrorCode.PAYOUT_ACCOUNT_ALREADY_REGISTERED);
        assertThat(account.getSellerId()).isEqualTo(셀러);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 셀러_id_없이는_등록할_수_없다(String sellerId) {
        DriverPayoutAccount account = 저장된_계좌();

        assertThatThrownBy(() -> account.registerSeller(sellerId, SellerStatus.PARTIALLY_APPROVED)).isInstanceOf(IllegalArgumentException.class);
        assertThat(account.isSellerRegistered()).isFalse();
    }

    @Test
    void 셀러_상태_없이는_등록할_수_없다() {
        DriverPayoutAccount account = 저장된_계좌();

        assertThatThrownBy(() -> account.registerSeller(셀러, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(account.isSellerRegistered()).isFalse();
    }

    @Test
    void 계좌를_바꾸면_은행_가린_번호_예금주가_바뀌고_셀러는_그대로다() {
        DriverPayoutAccount account = 등록된_계좌(SellerStatus.PARTIALLY_APPROVED);

        account.changeAccount(BankCode.SHINHAN, "1002003004005", "새예금주");

        assertThat(account.getBankCode()).isEqualTo(BankCode.SHINHAN);
        assertThat(account.getMaskedAccountNumber()).isEqualTo("*********4005");
        assertThat(account.getAccountHolder()).isEqualTo("새예금주");
        assertThat(account.getSellerId()).isEqualTo(셀러);
        assertThat(account.getBusinessType()).isEqualTo(BusinessType.CORPORATE);
    }

    @Test
    void 잘못된_계좌로는_바꿀_수_없고_기존_값이_남는다() {
        DriverPayoutAccount account = 등록된_계좌(SellerStatus.PARTIALLY_APPROVED);

        assertErrorCode(() -> account.changeAccount(BankCode.SHINHAN, "110-123", "새예금주"), SettlementErrorCode.INVALID_ACCOUNT_NUMBER);

        assertThat(account.getBankCode()).isEqualTo(은행);
        assertThat(account.getMaskedAccountNumber()).isEqualTo("********6789");
        assertThat(account.getAccountHolder()).isEqualTo(예금주);
    }

    @Test
    void 셀러_상태를_바꾼다() {
        DriverPayoutAccount account = 등록된_계좌(SellerStatus.PARTIALLY_APPROVED);

        account.changeSellerStatus(SellerStatus.KYC_REQUIRED);

        assertThat(account.getSellerStatus()).isEqualTo(SellerStatus.KYC_REQUIRED);
    }

    @Test
    void 셀러가_등록되지_않은_계좌는_상태를_바꿀_수_없다() {
        DriverPayoutAccount account = 저장된_계좌();

        assertThatThrownBy(() -> account.changeSellerStatus(SellerStatus.APPROVED)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @NullSource
    void 셀러_상태를_비울_수는_없다(SellerStatus status) {
        DriverPayoutAccount account = 등록된_계좌(SellerStatus.PARTIALLY_APPROVED);

        assertThatThrownBy(() -> account.changeSellerStatus(status)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, names = {"PARTIALLY_APPROVED", "APPROVED"})
    void 부분_승인이나_승인된_셀러에게만_지급할_수_있다(SellerStatus status) {
        assertThat(등록된_계좌(status).canReceivePayout()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, mode = EXCLUDE, names = {"PARTIALLY_APPROVED", "APPROVED"})
    void 본인인증이나_KYC_가_필요한_셀러에게는_지급할_수_없다(SellerStatus status) {
        assertThat(등록된_계좌(status).canReceivePayout()).isFalse();
    }

    private static DriverPayoutAccount 저장된_계좌() {
        return DriverPayoutAccount.load(계좌_번호, 기사, BusinessType.CORPORATE, 은행, "********6789", 예금주, null, null, 생성, null);
    }

    private static DriverPayoutAccount 등록된_계좌(SellerStatus status) {
        return DriverPayoutAccount.load(계좌_번호, 기사, BusinessType.CORPORATE, 은행, "********6789", 예금주, 셀러, status, 생성, 생성);
    }

    private static void assertErrorCode(Runnable action, SettlementErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
