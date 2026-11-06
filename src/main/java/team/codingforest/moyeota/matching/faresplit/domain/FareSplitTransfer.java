package team.codingforest.moyeota.matching.faresplit.domain;

import java.time.Instant;

/**
 *  송금 한 건 - 받는 사람(payer)을 뺀 탑승자마다 하나. 애그리거트 루트 FareSplit 을 거쳐서만 바뀐다
 */
public class FareSplitTransfer {
    private final Long memberId;
    private final Instant sentAt;
    private final Instant confirmedAt;
    private final int requestCount;
    private final Instant lastRequestedAt;
    private final Instant confirmRequestedAt;

    private FareSplitTransfer(Long memberId, Instant sentAt, Instant confirmedAt, int requestCount, Instant lastRequestedAt, Instant confirmRequestedAt) {
        this.memberId = memberId;
        this.sentAt = sentAt;
        this.confirmedAt = confirmedAt;
        this.requestCount = requestCount;
        this.lastRequestedAt = lastRequestedAt;
        this.confirmRequestedAt = confirmRequestedAt;
    }

    static FareSplitTransfer notSent(Long memberId) {
        return new FareSplitTransfer(memberId, null, null, 0, null, null);
    }

    public static FareSplitTransfer load(Long memberId, Instant sentAt, Instant confirmedAt, int requestCount, Instant lastRequestedAt, Instant confirmRequestedAt) {
        return new FareSplitTransfer(memberId, sentAt, confirmedAt, requestCount, lastRequestedAt, confirmRequestedAt);
    }

    public TransferStatus status() {
        if(confirmedAt != null) return TransferStatus.CONFIRMED;
        if(sentAt != null) return TransferStatus.SENT;

        return TransferStatus.NOT_SENT;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public Instant getLastRequestedAt() {
        return lastRequestedAt;
    }

    public Instant getConfirmRequestedAt() {
        return confirmRequestedAt;
    }
}
