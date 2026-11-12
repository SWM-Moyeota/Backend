package team.codingforest.moyeota.faresplit.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitRole;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 택시비 정산 - 애그리거트 루트. 방장(payer)이 택시비를 다 내고 실제 요금과 받을 계좌를 넣으면
 */
public class FareSplit {
	public static final Duration EXPIRES_AFTER = Duration.ofDays(1);
	public static final Duration REMIND_INTERVAL = Duration.ofHours(4);
	public static final int MAX_TRANSFER_REQUESTS = 6;
	private final Long partyId;
	private final Long payerId;
	private int totalFare;
	private Bank bank;
	private AccountNumber accountNumber;
	private FareSplitStatus status;
	private final Instant createdAt;
	private Instant closedAt;
	private final List<FareSplitTransfer> transfers;

	private FareSplit(Long partyId, Long payerId, int totalFare, Bank bank, AccountNumber accountNumber,
	                  FareSplitStatus status, Instant createdAt, Instant closedAt, List<FareSplitTransfer> transfers) {
		this.partyId = partyId;
		this.payerId = payerId;
		this.totalFare = totalFare;
		this.bank = bank;
		this.accountNumber = accountNumber;
		this.status = status;
		this.createdAt = createdAt;
		this.closedAt = closedAt;
		this.transfers = new ArrayList<>(transfers);
	}

	/**
	 * 정산 내역 생성
	 */
	public static FareSplit create(RideRoster party, Long payerId, int totalFare, Bank bank, AccountNumber accountNumber,
	                               List<FarePayment> payments, Instant now) {
		party.validateStarter(payerId);

		validatePayments(party.riderIds(), payments, totalFare);

		List<FareSplitTransfer> transfers = party.riderIds().stream()
				.map(riderId -> riderId.equals(payerId)
						? FareSplitTransfer.createPaid(riderId, amountOf(payments, riderId), now)
						: FareSplitTransfer.create(riderId, amountOf(payments, riderId)))
				.toList();

		return new FareSplit(party.partyId(), payerId, totalFare, bank, accountNumber, FareSplitStatus.OPEN, now, null, transfers);
	}

	public static FareSplit load(Long partyId, Long payerId, int totalFare, Bank bank, AccountNumber accountNumber,
	                             FareSplitStatus status, Instant createdAt, Instant closedAt, List<FareSplitTransfer> transfers) {
		return new FareSplit(partyId, payerId, totalFare, bank, accountNumber, status, createdAt, closedAt, transfers);
	}

	/**
	 * 보내는 사람이 보냈다고 누른다 (NOT_SENT → SENT)로 변경
	 */
	public boolean markSent(Long senderId, Instant now) {
		validateSender(senderId);
		validateOpen();

		FareSplitTransfer transfer = senderTransferOf(senderId);

		if (!transfer.isNotSent()) {
			return false;
		}

		transfer.markSent(now);

		return true;
	}

	/**
	 * 받는 사람이 받았다고 확인한다(→ CONFIRMED)
	 * 모든 송금이 확인되면 COMPLETED 로 닫고 계좌번호를 지운다
	 */
	public boolean confirmReceived(Long payerId, Long senderId, Instant now) {
		validatePayer(payerId);
		validateOpen();

		FareSplitTransfer transfer = senderTransferOf(senderId);

		if (transfer.isConfirmed()) {
			return false;
		}

		transfer.confirm(now);

		if (transfers.stream().allMatch(FareSplitTransfer::isConfirmed)) {
			close(FareSplitStatus.COMPLETED, now);
		}

		return true;
	}

	/**
	 * 받는 사람이 아직 못 받았다고 되돌린다(SENT → NOT_SENT)
	 */
	public void markNotReceived(Long payerId, Long senderId) {
		validatePayer(payerId);
		validateOpen();

		FareSplitTransfer transfer = senderTransferOf(senderId);

		if (!transfer.isSent()) {
			throw new BusinessException(FareSplitErrorCode.TRANSFER_NOT_SENT);
		}

		transfer.markNotReceived();
	}

	/**
	 * 받는 사람이 택시비·계좌·사람마다 부담 금액을 고친다. 누군가 보내기 전만 편집 가능
	 */
	public void edit(Long payerId, int totalFare, Bank bank, AccountNumber accountNumber, List<FarePayment> payments) {
		validatePayer(payerId);
		validateOpen();
		validateNotStarted();
		validatePayments(participantIds(), payments, totalFare);

		this.totalFare = totalFare;
		this.bank = bank;
		this.accountNumber = accountNumber;
		transfers.forEach(transfer -> transfer.changePayment(amountOf(payments, transfer.getMemberId())));
	}

	/**
	 * 송금 재요청 - 아직 안 보낸 사람 중 차례가 된 사람을 고르고 요청 횟수를 올린다.
	 */
	public FareSplitNotice remindTransfers(Instant now) {
		if (!isOpen() || accountNumber == null) {
			return FareSplitNotice.notDue();
		}

		List<FareSplitTransfer> due = transfers.stream()
				.filter(FareSplitTransfer::isNotSent)
				.filter(transfer -> isRequestDue(transfer, now))
				.toList();

		int requestCount = requestCountAt(now);
		due.forEach(transfer -> transfer.changeRequestCount(requestCount));

		return FareSplitNotice.send(paymentsOf(due));
	}

	/**
	 *  하루 지나도 만료되지 않았으면 닫히게 설정
	 */
	public boolean expire(Instant now) {
		if (!isOpen() || now.isBefore(createdAt.plus(EXPIRES_AFTER))) {
			return false;
		}

		close(FareSplitStatus.EXPIRED, now);

		return true;
	}

	/**
	 * 받는 사람이 탈퇴하면 계좌번호를 바로 지운다.
	 */
	public boolean forgetAccount() {
		if (accountNumber == null) {
			return false;
		}

		accountNumber = null;

		return true;
	}


	public void validateParticipant(Long memberId) {
		if (findTransfer(memberId).isEmpty()) {
			throw new BusinessException(FareSplitErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
		}
	}

	public void validatePayer(Long memberId) {
		validateParticipant(memberId);

		if (!isPayer(memberId)) {
			throw new BusinessException(FareSplitErrorCode.NOT_FARE_SPLIT_PAYER);
		}
	}

	private void validateSender(Long memberId) {
		validateParticipant(memberId);

		if (isPayer(memberId)) {
			throw new BusinessException(FareSplitErrorCode.NOT_FARE_SPLIT_SENDER);
		}
	}

	private void validateOpen() {
		if (!isOpen()) {
			throw new BusinessException(FareSplitErrorCode.FARE_SPLIT_CLOSED);
		}
	}

	private static void validatePayments(List<Long> participantIds, List<FarePayment> payments, int totalFare) {
		Set<Long> paidMemberIds = new HashSet<>(payments.stream().map(FarePayment::memberId).toList());

		// 유효한지 판별 정산 인원과 참여자 인원이 같은지
		if (payments.size() != participantIds.size() || !paidMemberIds.equals(new HashSet<>(participantIds))) {
			throw new BusinessException(FareSplitErrorCode.PAYMENT_TARGET_MISMATCH);
		}

		long paymentsTotal = payments.stream().mapToLong(FarePayment::amount).sum();

		if (paymentsTotal != totalFare) {
			throw new BusinessException(FareSplitErrorCode.PAYMENTS_NOT_EQUAL_FARE);
		}
	}

	private void validateNotStarted() {
		if (!senderTransfers().stream().allMatch(FareSplitTransfer::isNotSent)) {
			throw new BusinessException(FareSplitErrorCode.FARE_SPLIT_ALREADY_STARTED);
		}
	}

	public boolean isOpen() {
		return status == FareSplitStatus.OPEN;
	}

	public boolean isPayer(Long memberId) {
		return payerId.equals(memberId);
	}

	/**
	 * 송신자인지 정산자인지 role 정하기
	 */
	public FareSplitRole roleOf(Long memberId) {
		validateParticipant(memberId);

		return isPayer(memberId) ? FareSplitRole.PAYER : FareSplitRole.SENDER;
	}

	/**
	 * 모든 참여자마다 부담 금액.
	 */
	public List<FarePayment> payments() {
		return paymentsOf(transfers);
	}

	/**
	 * 보내는 사람마다 보낼 금액
	 */
	public List<FarePayment> senderPayments() {
		return paymentsOf(senderTransfers());
	}

	/**
	 * 내 부담 금액
	 */
	public int amountOf(Long memberId) {
		validateParticipant(memberId);

		return transferOf(memberId).getPayment();
	}

	/**
	 * 내 송금 상태 - 받는 사람은 보낼 것이 없어 비어 있다
	 */
	public Optional<TransferStatus> transferStatusOf(Long memberId) {
		validateParticipant(memberId);

		if (isPayer(memberId)) {
			return Optional.empty();
		}

		return Optional.of(transferOf(memberId).status());
	}

	/**
	 * 받을 계좌는 열린 정산에서만 보여 준다
	 */
	public Optional<AccountNumber> receivingAccount() {
		return isOpen() ? Optional.ofNullable(accountNumber) : Optional.empty();
	}

	public List<Long> senderIds() {
		return senderTransfers().stream().map(FareSplitTransfer::getMemberId).toList();
	}

	/**
	 * 받는 사람의 행을 뺀, 보내는 사람들의 송금
	 */
	public List<FareSplitTransfer> senderTransfers() {
		return transfers.stream().filter(transfer -> !isPayer(transfer.getMemberId())).toList();
	}

	/**
	 * 받는 사람과 보내는 사람 전원
	 */
	public List<Long> participantIds() {
		return transfers.stream().map(FareSplitTransfer::getMemberId).toList();
	}

	public Optional<FareSplitTransfer> findTransfer(Long memberId) {
		return transfers.stream().filter(transfer -> transfer.getMemberId().equals(memberId)).findFirst();
	}

	private static List<FarePayment> paymentsOf(List<FareSplitTransfer> transfers) {
		return transfers.stream().map(transfer -> new FarePayment(transfer.getMemberId(), transfer.getPayment())).toList();
	}

	private static int amountOf(List<FarePayment> payments, Long memberId) {
		return payments.stream()
				.filter(payment -> payment.memberId().equals(memberId))
				.findFirst()
				.orElseThrow()
				.amount();
	}

	private FareSplitTransfer transferOf(Long memberId) {
		return findTransfer(memberId)
				.orElseThrow(() -> new BusinessException(FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND));
	}

	private FareSplitTransfer senderTransferOf(Long senderId) {
		if (isPayer(senderId)) {
			throw new BusinessException(FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND);
		}

		return transferOf(senderId);
	}

	private int requestCountAt(Instant now) {
		long passedTurns = Duration.between(createdAt, now).dividedBy(REMIND_INTERVAL);

		return (int) Math.min(MAX_TRANSFER_REQUESTS, passedTurns + 1);
	}

	private boolean isRequestDue(FareSplitTransfer transfer, Instant now) {
		if (transfer.getRequestCount() >= MAX_TRANSFER_REQUESTS) {
			return false;
		}

		Instant dueAt = createdAt.plus(REMIND_INTERVAL.multipliedBy(transfer.getRequestCount()));

		return !now.isBefore(dueAt);
	}

	private void close(FareSplitStatus closedStatus, Instant now) {
		status = closedStatus;
		closedAt = now;
		accountNumber = null;
	}

	public Long getPartyId() {
		return partyId;
	}

	public Long getPayerId() {
		return payerId;
	}

	public int getTotalFare() {
		return totalFare;
	}

	public Bank getBank() {
		return bank;
	}

	public AccountNumber getAccountNumber() {
		return accountNumber;
	}

	public FareSplitStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public List<FareSplitTransfer> getTransfers() {
		return List.copyOf(transfers);
	}
}
