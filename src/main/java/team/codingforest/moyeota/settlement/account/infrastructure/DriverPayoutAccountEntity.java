package team.codingforest.moyeota.settlement.account.infrastructure;

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
import team.codingforest.moyeota.settlement.account.domain.BusinessType;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccount;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;

@Entity
@Getter
@Table(name = "driver_payout_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverPayoutAccountEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "taxi_driver_id", nullable = false, updatable = false)
    private Long taxiDriverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, updatable = false)
    private BusinessType businessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", nullable = false)
    private BankCode bankCode;

    @Column(name = "masked_account_number", nullable = false)
    private String maskedAccountNumber;

    @Column(name = "account_holder", nullable = false)
    private String accountHolder;

    @Column(name = "seller_id")
    private String sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "seller_status")
    private SellerStatus sellerStatus;

    private DriverPayoutAccountEntity(Long taxiDriverId, BusinessType businessType, BankCode bankCode, String maskedAccountNumber,
                                      String accountHolder, String sellerId, SellerStatus sellerStatus) {
        this.taxiDriverId = taxiDriverId;
        this.businessType = businessType;
        this.bankCode = bankCode;
        this.maskedAccountNumber = maskedAccountNumber;
        this.accountHolder = accountHolder;
        this.sellerId = sellerId;
        this.sellerStatus = sellerStatus;
    }

    public static DriverPayoutAccountEntity from(DriverPayoutAccount account) {
        return new DriverPayoutAccountEntity(account.getTaxiDriverId(), account.getBusinessType(), account.getBankCode(),
                account.getMaskedAccountNumber(), account.getAccountHolder(), account.getSellerId(), account.getSellerStatus());
    }

    public DriverPayoutAccountEntity update(DriverPayoutAccount account) {
        this.bankCode = account.getBankCode();
        this.maskedAccountNumber = account.getMaskedAccountNumber();
        this.accountHolder = account.getAccountHolder();
        this.sellerId = account.getSellerId();
        this.sellerStatus = account.getSellerStatus();
        return this;
    }

    public DriverPayoutAccount toDomain() {
        return DriverPayoutAccount.load(id, taxiDriverId, businessType, bankCode, maskedAccountNumber, accountHolder,
                sellerId, sellerStatus, getCreatedAt(), getUpdatedAt());
    }
}
