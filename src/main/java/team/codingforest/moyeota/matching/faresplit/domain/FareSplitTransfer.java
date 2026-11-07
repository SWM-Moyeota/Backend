package team.codingforest.moyeota.matching.faresplit.domain;

import java.time.Instant;

/**
 *  송금 한 건 - 받는 사람(payer)을 뺀 탑승자마다 하나. 애그리거트 루트 FareSplit 을 거쳐서만 바뀐다
 */
public class FareSplitTransfer {
    private final Long memberId;
    private Instant sentAt;
    private Instant confirmedAt;
    // 처리한 송금 요청 차례 수(1..TransferRequestSchedule.LIMIT). 방해 금지 시간·보냄 상태로 보내지 않은 차례도 센다
    private int requestCount;
    // 실제로 송금 요청 알림을 보낸 마지막 시각
    private Instant lastRequestedAt;
    private Instant confirmRequestedAt;

    private FareSplitTransfer(Long memberId, Instant sentAt, Instant confirmedAt, int requestCount, Instant lastRequestedAt, Instant confirmRequestedAt) {
        this.memberId = memberId;
        this.sentAt = sentAt;
        this.confirmedAt = confirmedAt;
        this.requestCount = requestCount;
        this.lastRequestedAt = lastRequestedAt;
        this.confirmRequestedAt = confirmRequestedAt;
    }

    /** 정산을 시작하며 첫 송금 요청(1번째)을 보낸 NOT_SENT 송금 */
    static FareSplitTransfer requested(Long memberId, Instant now) {
        return new FareSplitTransfer(memberId, null, null, 1, now, null);
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

    void passRequestTurns(int count) {
        requestCount = count;
    }

    void markRequested(Instant now) {
        lastRequestedAt = now;
    }

    void markConfirmRequested(Instant now) {
        confirmRequestedAt = now;
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
