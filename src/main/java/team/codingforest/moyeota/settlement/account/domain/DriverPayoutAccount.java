package team.codingforest.moyeota.settlement.account.domain;

import lombok.Getter;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 기사 지급 계좌. 계좌번호 원문은 토스 셀러에만 넘기고 여기에는 가린 번호만 남김
 * 우리 행을 먼저 저장해 refSellerId 를 정한 뒤 토스 셀러를 등록하고 셀러 id·상태를 채움
 */
@Getter
public class DriverPayoutAccount {
    private static final String REF_SELLER_ID_FORMAT = "pa%05d";
    private static final int MAX_REF_SELLER_ID_LENGTH = 20;
    private static final Pattern ACCOUNT_NUMBER_FORMAT = Pattern.compile("\\d{1,14}");
    private static final int MAX_ACCOUNT_HOLDER_LENGTH = 50;
    private static final int VISIBLE_DIGITS = 4;
    private static final char MASK = '*';
    private static final Set<SellerStatus> PAYABLE = Set.of(SellerStatus.PARTIALLY_APPROVED, SellerStatus.APPROVED);
    private static final Duration REGISTRATION_TIMEOUT = Duration.ofMinutes(1);

    private final Long id;
    private final Long taxiDriverId;
    private final BusinessType businessType;
    private BankCode bankCode;
    private String maskedAccountNumber;
    private String accountHolder;
    private String sellerId;
    private SellerStatus sellerStatus;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DriverPayoutAccount(Long id, Long taxiDriverId, BusinessType businessType, BankCode bankCode, String maskedAccountNumber,
                                String accountHolder, String sellerId, SellerStatus sellerStatus, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.taxiDriverId = taxiDriverId;
        this.businessType = businessType;
        this.bankCode = bankCode;
        this.maskedAccountNumber = maskedAccountNumber;
        this.accountHolder = accountHolder;
        this.sellerId = sellerId;
        this.sellerStatus = sellerStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static DriverPayoutAccount create(Long taxiDriverId, BusinessType businessType, BankCode bankCode,
                                             String accountNumber, String accountHolder) {
        if(taxiDriverId == null || businessType == null) throw new BusinessException(SettlementErrorCode.INVALID_PAYOUT_ACCOUNT);
        validateAccount(bankCode, accountNumber, accountHolder);

        return new DriverPayoutAccount(null, taxiDriverId, businessType, bankCode, mask(accountNumber),
                accountHolder, null, null, null, null);
    }

    public static DriverPayoutAccount load(Long id, Long taxiDriverId, BusinessType businessType, BankCode bankCode, String maskedAccountNumber,
                                           String accountHolder, String sellerId, SellerStatus sellerStatus, Instant createdAt, Instant updatedAt) {
        return new DriverPayoutAccount(id, taxiDriverId, businessType, bankCode, maskedAccountNumber, accountHolder,
                sellerId, sellerStatus, createdAt, updatedAt);
    }

    public String refSellerId() {
        if(id == null) throw new IllegalStateException("저장되지 않은 지급 계좌에는 셀러 참조 id 가 없습니다.");

        String refSellerId = REF_SELLER_ID_FORMAT.formatted(id);
        if(refSellerId.length() > MAX_REF_SELLER_ID_LENGTH) throw new IllegalStateException("셀러 참조 id 가 20자를 넘습니다. id=" + id);
        return refSellerId;
    }

    public void registerSeller(String sellerId, SellerStatus sellerStatus) {
        if(isSellerRegistered()) throw new BusinessException(SettlementErrorCode.PAYOUT_ACCOUNT_ALREADY_REGISTERED);
        if(sellerId == null || sellerId.isBlank() || sellerStatus == null) throw new IllegalArgumentException("셀러 id 와 상태가 필요합니다.");

        this.sellerId = sellerId;
        this.sellerStatus = sellerStatus;
    }

    public void changeAccount(BankCode bankCode, String accountNumber, String accountHolder) {
        validateAccount(bankCode, accountNumber, accountHolder);

        this.bankCode = bankCode;
        this.maskedAccountNumber = mask(accountNumber);
        this.accountHolder = accountHolder;
    }

    public void changeSellerStatus(SellerStatus sellerStatus) {
        if(!isSellerRegistered()) throw new IllegalStateException("토스에 등록되지 않은 지급 계좌입니다. id=" + id);
        if(sellerStatus == null) throw new IllegalArgumentException("셀러 상태가 필요합니다.");

        this.sellerStatus = sellerStatus;
    }

    public boolean isSellerRegistered() {
        return sellerId != null;
    }

    public boolean canReceivePayout() {
        return isSellerRegistered() && PAYABLE.contains(sellerStatus);
    }

    /**
     * 셀러 등록을 마치지 못하고 버려진 행인지. 결제사 호출 시간(연결 3초, 응답 10초)보다 넉넉히 지났으면 진행 중인 등록이 아님
     *
     * @param now 지금 시각
     * @return 셀러 없이 등록 제한 시간이 지났으면 true
     */
    public boolean isAbandonedRegistration(Instant now) {
        return !isSellerRegistered() && createdAt != null && createdAt.plus(REGISTRATION_TIMEOUT).isBefore(now);
    }

    private static void validateAccount(BankCode bankCode, String accountNumber, String accountHolder) {
        if(bankCode == null) throw new BusinessException(SettlementErrorCode.INVALID_PAYOUT_ACCOUNT);
        if(accountNumber == null || !ACCOUNT_NUMBER_FORMAT.matcher(accountNumber).matches()) {
            throw new BusinessException(SettlementErrorCode.INVALID_ACCOUNT_NUMBER);
        }
        if(accountHolder == null || accountHolder.isBlank() || accountHolder.length() > MAX_ACCOUNT_HOLDER_LENGTH) {
            throw new BusinessException(SettlementErrorCode.INVALID_ACCOUNT_HOLDER);
        }
    }

    private static String mask(String accountNumber) {
        int hidden = accountNumber.length() - VISIBLE_DIGITS;
        if(hidden <= 0) return String.valueOf(MASK).repeat(accountNumber.length());
        return String.valueOf(MASK).repeat(hidden) + accountNumber.substring(hidden);
    }
}
