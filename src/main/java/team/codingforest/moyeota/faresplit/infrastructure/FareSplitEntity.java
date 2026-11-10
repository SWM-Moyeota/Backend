package team.codingforest.moyeota.faresplit.infrastructure;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.common.crypto.EncryptedStringConverter;
import team.codingforest.moyeota.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.FareSplitTransfer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "fare_split")
public class FareSplitEntity extends BaseTimeEntity {

    // 방 하나당 정산 하나이기에 1:1 매핑
    @Id
    @Column(name = "match_id")
    private Long partyId;

    @Column(name = "payer_id", nullable = false, updatable = false)
    private Long payerId;

    @Column(name = "total_fare", nullable = false)
    private int totalFare;

    @Column(name = "amount_per_person", nullable = false)
    private int amountPerPerson;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", nullable = false, length = 20)
    private Bank bankCode;

    // 계좌번호는 암호화하여 저장
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number")
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FareSplitStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "fareSplit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FareSplitTransferEntity> transfers = new ArrayList<>();

    private FareSplitEntity(Long partyId, Long payerId, int totalFare, int amountPerPerson, Bank bankCode, String accountNumber,
                            FareSplitStatus status, Instant closedAt) {
        this.partyId = partyId;
        this.payerId = payerId;
        this.totalFare = totalFare;
        this.amountPerPerson = amountPerPerson;
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.status = status;
        this.closedAt = closedAt;
    }

    public static FareSplitEntity from(FareSplit split) {
        AccountNumber account = split.getAccountNumber();
        FareSplitEntity entity = new FareSplitEntity(split.getPartyId(), split.getPayerId(), split.getTotalFare(), split.getAmountPerPerson(),
                split.getBank(), account == null ? null : account.value(), split.getStatus(), split.getClosedAt());

        for(FareSplitTransfer transfer : split.getTransfers()) {
            entity.transfers.add(FareSplitTransferEntity.of(entity, transfer));
        }

        return entity;
    }

    // 변경될 수 있는 값들 계좌번호, 총 금액 등
    void update(FareSplit split) {
        AccountNumber account = split.getAccountNumber();
        this.totalFare = split.getTotalFare();
        this.amountPerPerson = split.getAmountPerPerson();
        this.bankCode = split.getBank();
        this.accountNumber = account == null ? null : account.value();
        this.status = split.getStatus();
        this.closedAt = split.getClosedAt();

        for(FareSplitTransferEntity transfer : transfers) {
            split.findTransfer(transfer.getMemberId()).ifPresent(transfer::update);
        }
    }

    // userId 순으로 정렬
    public FareSplit toDomain() {
        List<FareSplitTransfer> ordered = transfers.stream()
                .sorted(Comparator.comparing(FareSplitTransferEntity::getMemberId))
                .map(FareSplitTransferEntity::toDomain)
                .toList();

        return FareSplit.load(partyId, payerId, totalFare, amountPerPerson, bankCode,
                accountNumber == null ? null : new AccountNumber(accountNumber), status, getCreatedAt(), closedAt, ordered);
    }
}
