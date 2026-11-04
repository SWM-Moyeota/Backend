package team.codingforest.moyeota.settlement.ledger.domain;

import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccount;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.time.Instant;
import java.util.Set;

/**
 * 정산서 하나를 결제사에 보낸 지급 시도 한 번. 받을 셀러와 계좌는 요청한 시점의 값을 남김
 */
public class Payout {
    private static final String PAYOUT_KEY_PREFIX = "moyeota-payout-";
    private static final int MAX_REASON_LENGTH = 255;

    private static final Set<PayoutStatus> AWAITING_RESULT = Set.of(PayoutStatus.REQUESTED, PayoutStatus.UNKNOWN);
    public static final Set<PayoutStatus> ACTIVE_STATUSES = Set.of(PayoutStatus.REQUESTED, PayoutStatus.UNKNOWN, PayoutStatus.SUCCEED);
    private static final Set<PayoutStatus> FINISHED = Set.of(PayoutStatus.SUCCEED, PayoutStatus.FAILED);

    private final Long id;
    private final Long settlementId;
    private final Integer amount;
    private PayoutStatus status;
    private String pgId;
    private final String sellerId;
    private final BankCode bankCode;
    private final String maskedAccountNumber;
    private final String accountHolder;
    private String failReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Payout(Long id, Long settlementId, Integer amount, PayoutStatus status, String pgId, String sellerId, BankCode bankCode,
                   String maskedAccountNumber, String accountHolder, String failReason, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.settlementId = settlementId;
        this.amount = amount;
        this.status = status;
        this.pgId = pgId;
        this.sellerId = sellerId;
        this.bankCode = bankCode;
        this.maskedAccountNumber = maskedAccountNumber;
        this.accountHolder = accountHolder;
        this.failReason = failReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Payout request(Settlement settlement, DriverPayoutAccount account) {
        if(account == null || !account.canReceivePayout()) throw new IllegalArgumentException("지급받을 수 있는 셀러가 없습니다.");
        if(!settlement.isUnpaid()) throw new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_STATUS);
        if(settlement.getAmount() <= 0) throw new BusinessException(SettlementErrorCode.INVALID_PAYOUT_AMOUNT);

        return new Payout(null, settlement.getId(), settlement.getAmount(), PayoutStatus.REQUESTED, null, account.getSellerId(),
                account.getBankCode(), account.getMaskedAccountNumber(), account.getAccountHolder(), null, null, null);
    }

    public static Payout load(Long id, Long settlementId, Integer amount, PayoutStatus status, String pgId, String sellerId, BankCode bankCode,
                              String maskedAccountNumber, String accountHolder, String failReason, Instant createdAt, Instant updatedAt) {
        return new Payout(id, settlementId, amount, status, pgId, sellerId, bankCode, maskedAccountNumber, accountHolder, failReason,
                createdAt, updatedAt);
    }

    public String payoutKey() {
        if(id == null) throw new IllegalStateException("저장되지 않은 지급에는 지급 키가 없습니다.");

        return PAYOUT_KEY_PREFIX + id;
    }

    public void succeed(String pgId) {
        if(!AWAITING_RESULT.contains(status)) throw new BusinessException(SettlementErrorCode.INVALID_PAYOUT_STATUS);

        this.status = PayoutStatus.SUCCEED;
        this.pgId = pgId;
        this.failReason = null;
    }

    public void fail(String reason) {
        if(!AWAITING_RESULT.contains(status)) throw new BusinessException(SettlementErrorCode.INVALID_PAYOUT_STATUS);

        this.status = PayoutStatus.FAILED;
        this.failReason = truncate(reason);
    }

    public void markUnknown(String reason) {
        if(status != PayoutStatus.REQUESTED) throw new BusinessException(SettlementErrorCode.INVALID_PAYOUT_STATUS);

        this.status = PayoutStatus.UNKNOWN;
        this.failReason = truncate(reason);
    }

    public boolean isActive() {
        return ACTIVE_STATUSES.contains(status);
    }

    public boolean isFinished() {
        return FINISHED.contains(status);
    }

    public PayoutOrder toOrder() {
        return new PayoutOrder(id, payoutKey(), amount, sellerId);
    }

    private static String truncate(String reason) {
        if(reason == null || reason.length() <= MAX_REASON_LENGTH) return reason;

        return reason.substring(0, MAX_REASON_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public Long getSettlementId() {
        return settlementId;
    }

    public Integer getAmount() {
        return amount;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public String getPgId() {
        return pgId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public BankCode getBankCode() {
        return bankCode;
    }

    public String getMaskedAccountNumber() {
        return maskedAccountNumber;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public String getFailReason() {
        return failReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
