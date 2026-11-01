package team.codingforest.moyeota.settlement.ledger.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;

@Entity
@Getter
@Table(name = "payout")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayoutEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false, updatable = false)
    private Long settlementId;

    @Column(name = "amount", nullable = false, updatable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayoutStatus status;

    @Column(name = "pg_id")
    private String pgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", nullable = false, updatable = false)
    private BankCode bankCode;

    @Column(name = "account_number", nullable = false, updatable = false)
    private String accountNumber;

    @Column(name = "account_holder", nullable = false, updatable = false)
    private String accountHolder;

    @Column(name = "fail_reason")
    private String failReason;

    private PayoutEntity(Long settlementId, Integer amount, PayoutStatus status, String pgId, BankCode bankCode,
                         String accountNumber, String accountHolder, String failReason) {
        this.settlementId = settlementId;
        this.amount = amount;
        this.status = status;
        this.pgId = pgId;
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.failReason = failReason;
    }

    public static PayoutEntity from(Payout payout) {
        return new PayoutEntity(payout.getSettlementId(), payout.getAmount(), payout.getStatus(), payout.getPgId(), payout.getBankCode(),
                payout.getAccountNumber(), payout.getAccountHolder(), payout.getFailReason());
    }

    public PayoutEntity update(Payout payout) {
        this.status = payout.getStatus();
        this.pgId = payout.getPgId();
        this.failReason = payout.getFailReason();
        return this;
    }

    public Payout toDomain() {
        return Payout.load(id, settlementId, amount, status, pgId, bankCode, accountNumber, accountHolder, failReason,
                getCreatedAt(), getUpdatedAt());
    }
}
