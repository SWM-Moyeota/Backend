package team.codingforest.moyeota.faresplit.domain;

import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;

import java.time.Instant;

/**
 *  정산 기록에 대한 유저가 금액을 지출한 내역과 확인 시간, 방장은 결제했기에 confirmedAt이 채워짐
 */
public class FareSplitTransfer {
	private final Long memberId;
	private int payment;
	private Instant sentAt;
	private Instant confirmedAt;
	private int requestCount;

	private FareSplitTransfer(Long memberId, int payment, Instant sentAt, Instant confirmedAt, int requestCount) {
		this.memberId = memberId;
		this.payment = payment;
		this.sentAt = sentAt;
		this.confirmedAt = confirmedAt;
		this.requestCount = requestCount;
	}

	static FareSplitTransfer create(Long memberId, int payment) {
		return new FareSplitTransfer(memberId, payment, null, null, 1);
	}

	/**
	 *  방장은 이미 지불했기에 confirmedAt 값이 채워짐
	 */
	static FareSplitTransfer createPaid(Long memberId, int payment, Instant now) {
		return new FareSplitTransfer(memberId, payment, now, now, 0);
	}

	public static FareSplitTransfer load(Long memberId, int payment, Instant sentAt, Instant confirmedAt, int requestCount) {
		return new FareSplitTransfer(memberId, payment, sentAt, confirmedAt, requestCount);
	}

	public TransferStatus status() {
		return TransferStatus.of(sentAt, confirmedAt);
	}

	boolean isNotSent() {
		return status() == TransferStatus.NOT_SENT;
	}

	boolean isSent() {
		return status() == TransferStatus.SENT;
	}

	boolean isConfirmed() {
		return status() == TransferStatus.CONFIRMED;
	}

	void changePayment(int payment) {
		this.payment = payment;
	}

	void markSent(Instant now) {
		sentAt = now;
	}

	void markNotReceived() {
		sentAt = null;
	}

	void confirm(Instant now) {
		confirmedAt = now;
	}

	void changeRequestCount(int requestCount) {
		this.requestCount = requestCount;
	}

	public Long getMemberId() {
		return memberId;
	}

	public int getPayment() {
		return payment;
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
}
