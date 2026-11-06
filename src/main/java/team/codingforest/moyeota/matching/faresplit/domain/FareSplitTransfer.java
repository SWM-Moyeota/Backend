package team.codingforest.moyeota.matching.faresplit.domain;

import java.time.Instant;

/**
 *  송금 한 건 - 받는 사람(payer)을 뺀 탑승자마다 하나. 애그리거트 루트 FareSplit 을 거쳐서만 바뀐다
 */
public class FareSplitTransfer {
    private final Long memberId;
    private Instant sentAt;
    private Instant confirmedAt;
    private final int requestCount;
    private final Instant lastRequestedAt;
    private Instant confirmRequestedAt;

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
        return TransferStatus.of(sentAt, confirmedAt);
    }

    void markSent(Instant now) {
        sentAt = now;
    }

    void confirm(Instant now) {
        confirmedAt = now;
    }

    /** SENT → NOT_SENT. 입금 확인 요청도 다시 할 수 있게 지운다 */
    void markNotReceived() {
        sentAt = null;
        confirmRequestedAt = null;
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
