package team.codingforest.moyeota.faresplit.domain;

import java.time.Instant;

/**
 *  송금 한 건 - 받는 사람(payer)을 뺀 탑승자마다 하나. 애그리거트 루트 FareSplit 을 거쳐서만 바뀐다
 */
public class FareSplitTransfer {
    private final Long memberId;
    private Instant sentAt;
    private Instant confirmedAt;
    // 마지막 송금 요청 시각 - 첫 요청, 재요청, 못 받았어요 알림. 재요청은 이 시각부터 센다
    private Instant lastRequestedAt;
    private Instant confirmRequestedAt;

    private FareSplitTransfer(Long memberId, Instant sentAt, Instant confirmedAt, Instant lastRequestedAt, Instant confirmRequestedAt) {
        this.memberId = memberId;
        this.sentAt = sentAt;
        this.confirmedAt = confirmedAt;
        this.lastRequestedAt = lastRequestedAt;
        this.confirmRequestedAt = confirmRequestedAt;
    }

    /** 정산을 시작하며 첫 송금 요청을 보낸 NOT_SENT 송금 */
    static FareSplitTransfer requested(Long memberId, Instant now) {
        return new FareSplitTransfer(memberId, null, null, now, null);
    }

    public static FareSplitTransfer load(Long memberId, Instant sentAt, Instant confirmedAt, Instant lastRequestedAt, Instant confirmRequestedAt) {
        return new FareSplitTransfer(memberId, sentAt, confirmedAt, lastRequestedAt, confirmRequestedAt);
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

    void markRequested(Instant now) {
        lastRequestedAt = now;
    }

    void markConfirmRequested(Instant now) {
        confirmRequestedAt = now;
    }

    /** SENT → NOT_SENT. 입금 확인 요청도 다시 할 수 있게 지우고, 보내는 사람에게 가는 못 받았어요 알림을 송금 요청으로 친다 */
    void markNotReceived(Instant now) {
        sentAt = null;
        confirmRequestedAt = null;
        lastRequestedAt = now;
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

    public Instant getLastRequestedAt() {
        return lastRequestedAt;
    }

    public Instant getConfirmRequestedAt() {
        return confirmRequestedAt;
    }
}
