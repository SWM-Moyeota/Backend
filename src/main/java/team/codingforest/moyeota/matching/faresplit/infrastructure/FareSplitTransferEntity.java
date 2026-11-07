package team.codingforest.moyeota.matching.faresplit.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitTransfer;

import java.time.Instant;

@Entity
@Getter
@IdClass(FareSplitTransferId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "fare_split_transfer")
public class FareSplitTransferEntity extends BaseTimeEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private FareSplitEntity fareSplit;

    @Id
    @Column(name = "user_id")
    private Long memberId;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "request_count", nullable = false)
    private short requestCount;

    @Column(name = "last_requested_at")
    private Instant lastRequestedAt;

    @Column(name = "confirm_requested_at")
    private Instant confirmRequestedAt;

    private FareSplitTransferEntity(FareSplitEntity fareSplit, Long memberId) {
        this.fareSplit = fareSplit;
        this.memberId = memberId;
    }

    public static FareSplitTransferEntity of(FareSplitEntity fareSplit, FareSplitTransfer transfer) {
        FareSplitTransferEntity entity = new FareSplitTransferEntity(fareSplit, transfer.getMemberId());
        entity.sentAt = transfer.getSentAt();
        entity.confirmedAt = transfer.getConfirmedAt();
        entity.requestCount = (short) transfer.getRequestCount();
        entity.lastRequestedAt = transfer.getLastRequestedAt();
        entity.confirmRequestedAt = transfer.getConfirmRequestedAt();

        return entity;
    }

    public FareSplitTransfer toDomain() {
        return FareSplitTransfer.load(memberId, sentAt, confirmedAt, requestCount, lastRequestedAt, confirmRequestedAt);
    }
}
