package team.codingforest.moyeota.matching.faresplit.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.time.QuietHours;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 *  택시비 정산 - 애그리거트 루트. 함께 탄 사람 중 한 명(payer)이 택시비를 다 내고 실제 요금과 받을 계좌를 넣으면
 *  나머지 탑승자가 1/N 을 각자 송금한다. 앱은 돈을 옮기지 않는다. 방마다 하나
 */
public class FareSplit {
    public static final int MIN_TOTAL_FARE = 1_000;
    public static final int MAX_TOTAL_FARE = 500_000;
    static final int MIN_RIDERS = 2;
    static final int ROUNDING_UNIT = 10;
    // 시작하고 이만큼 지나도 열려 있으면 EXPIRED 로 닫는다
    public static final Duration EXPIRES_AFTER = Duration.ofDays(7);
    // 보냈다고 누르고 이만큼 지나도 확인되지 않으면 받는 사람에게 입금 확인을 한 번 요청한다
    public static final Duration CONFIRM_REQUEST_AFTER = Duration.ofHours(24);

    private final Long partyId;
    private final Long payerId;
    private int totalFare;
    private int amountPerPerson;
    private Bank bank;
    // 닫히면(COMPLETED·EXPIRED) null
    private AccountNumber accountNumber;
    private FareSplitStatus status;
    private final Instant createdAt;
    private Instant closedAt;
    // 방 합류 순. payer 의 행은 없다
    private final List<FareSplitTransfer> transfers;

    private FareSplit(Long partyId, Long payerId, int totalFare, int amountPerPerson, Bank bank, AccountNumber accountNumber,
                      FareSplitStatus status, Instant createdAt, Instant closedAt, List<FareSplitTransfer> transfers) {
        this.partyId = partyId;
        this.payerId = payerId;
        this.totalFare = totalFare;
        this.amountPerPerson = amountPerPerson;
        this.bank = bank;
        this.accountNumber = accountNumber;
        this.status = status;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
        this.transfers = new ArrayList<>(transfers);
    }

    /**
     *  정산 시작. 동승이 끝난(FINISHED) 방의 탑승자만 payer 가 될 수 있고, payer 를 포함한 탑승자가 2명 이상이어야 한다.
     *  payer 를 뺀 탑승자마다 NOT_SENT 송금을 만들고, 이때 보내는 첫 송금 요청을 1번째 차례로 센다. 같은 방에 이미 정산이 있는지는 서비스가 본다
     */
    public static FareSplit open(Party party, Long payerId, int totalFare, Bank bank, AccountNumber accountNumber, Instant now) {
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(accountNumber, "accountNumber");

        if(!party.hasMember(payerId)) throw new BusinessException(MatchingErrorCode.NOT_PARTY_MEMBER);

        if(party.getStatus() != PartyStatus.FINISHED) throw new BusinessException(MatchingErrorCode.PARTY_NOT_FINISHED);

        List<Long> riderIds = riderIds(party);

        if(!riderIds.contains(payerId)) throw new BusinessException(MatchingErrorCode.NOT_A_RIDER);

        if(riderIds.size() < MIN_RIDERS) throw new BusinessException(MatchingErrorCode.NOT_ENOUGH_RIDERS);

        validateFare(totalFare);

        List<FareSplitTransfer> transfers = riderIds.stream()
                .filter(id -> !id.equals(payerId))
                .map(id -> FareSplitTransfer.requested(id, now))
                .toList();

        return new FareSplit(party.getId(), payerId, totalFare, amountPerPerson(totalFare, riderIds.size()), bank, accountNumber,
                FareSplitStatus.OPEN, now, null, transfers);
    }

    public static FareSplit load(Long partyId, Long payerId, int totalFare, int amountPerPerson, Bank bank, AccountNumber accountNumber,
                                 FareSplitStatus status, Instant createdAt, Instant closedAt, List<FareSplitTransfer> transfers) {
        return new FareSplit(partyId, payerId, totalFare, amountPerPerson, bank, accountNumber, status, createdAt, closedAt, transfers);
    }

    /**
     *  탑승자 - 출발지에 도착했고 노쇼가 아닌 멤버(합류 순). 도착 없이 끝난 방(약속을 정하지 않은 구버전 종료)은 탑승자가 없다
     */
    static List<Long> riderIds(Party party) {
        return party.getMembers().stream()
                .filter(m -> m.isArrived() && !m.isNoShow())
                .map(PartyMember::getMemberId)
                .toList();
    }

    /**
     *  1인당 보낼 금액 - totalFare / 탑승자 수를 10원 단위로 올린다. 올린 만큼 payer 의 몫(payerShare)이 줄어든다
     */
    static int amountPerPerson(int totalFare, int riders) {
        int unit = riders * ROUNDING_UNIT;

        return (totalFare + unit - 1) / unit * ROUNDING_UNIT;
    }

    private static void validateFare(int totalFare) {
        if(totalFare < MIN_TOTAL_FARE || totalFare > MAX_TOTAL_FARE) throw new BusinessException(MatchingErrorCode.INVALID_FARE);
    }

    // ───────────────────────── 송금 처리 ─────────────────────────

    /**
     *  보내는 사람이 보냈다고 누른다(NOT_SENT → SENT). 이미 SENT·CONFIRMED 면 그대로 두고 false - 다시 눌러도 된다
     */
    public boolean markSent(Long memberId, Instant now) {
        if(roleOf(memberId) != FareSplitRole.SENDER) throw new BusinessException(MatchingErrorCode.NOT_FARE_SPLIT_SENDER);

        FareSplitTransfer transfer = transferOf(memberId);

        if(transfer.status() != TransferStatus.NOT_SENT) return false;

        ensureOpen();
        transfer.markSent(now);

        return true;
    }

    /**
     *  payer 가 받았다고 확인한다(→ CONFIRMED). 보냈다고 누르기 전이어도 된다(현금 등). 이미 CONFIRMED 면 그대로 두고 false.
     *  모든 송금이 확인되면 COMPLETED 로 닫고 계좌번호를 지운다
     */
    public boolean confirmReceived(Long requesterId, Long senderId, Instant now) {
        ensurePayer(requesterId);

        FareSplitTransfer transfer = transferOf(senderId);

        if(transfer.status() == TransferStatus.CONFIRMED) return false;

        ensureOpen();
        transfer.confirm(now);

        if(transfers.stream().allMatch(t -> t.status() == TransferStatus.CONFIRMED)) close(FareSplitStatus.COMPLETED, now);

        return true;
    }

    /**
     *  payer 가 아직 못 받았다고 되돌린다(SENT → NOT_SENT). SENT 가 아니면 TRANSFER_NOT_SENT
     */
    public void markNotReceived(Long requesterId, Long senderId) {
        ensurePayer(requesterId);

        FareSplitTransfer transfer = transferOf(senderId);

        ensureOpen();

        if(transfer.status() != TransferStatus.SENT) throw new BusinessException(MatchingErrorCode.TRANSFER_NOT_SENT);

        transfer.markNotReceived();
    }

    // ───────────────────────── 수정·취소 ─────────────────────────

    /**
     *  payer 가 택시비·계좌를 고친다. 아무도 보내기 전(모든 송금 NOT_SENT)인 OPEN 정산만. 1인당 금액을 다시 계산한다
     */
    public void edit(Long requesterId, int totalFare, Bank bank, AccountNumber accountNumber) {
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(accountNumber, "accountNumber");

        ensureNotStarted(requesterId);
        validateFare(totalFare);

        this.totalFare = totalFare;
        this.amountPerPerson = amountPerPerson(totalFare, transfers.size() + 1);
        this.bank = bank;
        this.accountNumber = accountNumber;
    }

    /**
     *  취소는 행을 지우므로 여기서는 할 수 있는지만 본다. 수정과 같은 조건이다
     */
    public void ensureCancelable(Long requesterId) {
        ensureNotStarted(requesterId);
    }

    /** payer 가 아니면 NOT_FARE_SPLIT_PAYER(정산에 없는 사람은 NOT_FARE_SPLIT_PARTICIPANT) */
    public void ensurePayer(Long memberId) {
        if(roleOf(memberId) != FareSplitRole.PAYER) throw new BusinessException(MatchingErrorCode.NOT_FARE_SPLIT_PAYER);
    }

    private void ensureNotStarted(Long requesterId) {
        ensurePayer(requesterId);
        ensureOpen();

        if(transfers.stream().anyMatch(t -> t.status() != TransferStatus.NOT_SENT)) {
            throw new BusinessException(MatchingErrorCode.FARE_SPLIT_ALREADY_STARTED);
        }
    }

    private void ensureOpen() {
        if(status != FareSplitStatus.OPEN) throw new BusinessException(MatchingErrorCode.FARE_SPLIT_CLOSED);
    }

    private FareSplitTransfer transferOf(Long memberId) {
        return findTransfer(memberId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND));
    }

    // 닫히면 계좌번호는 남기지 않는다
    private void close(FareSplitStatus closedStatus, Instant now) {
        status = closedStatus;
        closedAt = now;
        accountNumber = null;
    }

    // ───────────────────────── 알림·만료·탈퇴 ─────────────────────────

    /**
     *  송금 재요청 차례(2..4번째)를 처리한다. 확인되지 않은 송금 중 차례가 지난 것의 차례를 넘기고, 그중 아직 안 보낸(NOT_SENT) 사람에게 보낸다.
     *  보냈다고 누른(SENT) 사람은 차례만 넘긴다 - 나중에 못 받았어요로 되돌아와도 지난 차례를 몰아 받지 않는다.
     *  서버가 멈춰 여러 차례가 밀렸으면 한 번만 보낸다. 방해 금지 시간에는 보내지 않고 차례만 넘긴다(출발 재촉과 같다).
     *  받을 계좌가 지워졌으면(받는 사람 탈퇴) 보낼 곳이 없어 재요청하지 않는다
     */
    public FareSplitNotice remindTransfers(Instant now) {
        if(status != FareSplitStatus.OPEN || accountNumber == null) return FareSplitNotice.notDue();

        int passed = TransferRequestSchedule.passedCount(createdAt, now);

        List<FareSplitTransfer> due = transfers.stream()
                .filter(t -> t.status() != TransferStatus.CONFIRMED && t.getRequestCount() < passed)
                .toList();

        if(due.isEmpty()) return FareSplitNotice.notDue();

        due.forEach(t -> t.passRequestTurns(passed));

        if(QuietHours.contains(now)) return FareSplitNotice.silent();

        List<FareSplitTransfer> recipients = due.stream().filter(t -> t.status() == TransferStatus.NOT_SENT).toList();
        recipients.forEach(t -> t.markRequested(now));

        return FareSplitNotice.send(recipients.stream().map(FareSplitTransfer::getMemberId).toList());
    }

    /**
     *  입금 확인 요청 - 보냈다고 누르고 24시간이 지나도 확인되지 않은 송금마다 한 번, 받는 사람에게 묶어서 보낸다.
     *  방해 금지 시간에는 아무것도 바꾸지 않고 끝날 때까지 기다린다. 못 받았어요로 되돌아갔다가 다시 보내면 그때부터 다시 센다
     */
    public FareSplitNotice requestConfirm(Instant now) {
        if(status != FareSplitStatus.OPEN || QuietHours.contains(now)) return FareSplitNotice.notDue();

        List<FareSplitTransfer> due = transfers.stream()
                .filter(t -> t.status() == TransferStatus.SENT && t.getConfirmRequestedAt() == null)
                .filter(t -> !now.isBefore(t.getSentAt().plus(CONFIRM_REQUEST_AFTER)))
                .toList();

        if(due.isEmpty()) return FareSplitNotice.notDue();

        due.forEach(t -> t.markConfirmRequested(now));

        return FareSplitNotice.send(List.of(payerId));
    }

    /**
     *  시작하고 7일이 지나도 열려 있으면 EXPIRED 로 닫고 계좌번호를 지운다. 닫았으면 true
     */
    public boolean expire(Instant now) {
        if(status != FareSplitStatus.OPEN || now.isBefore(createdAt.plus(EXPIRES_AFTER))) return false;

        close(FareSplitStatus.EXPIRED, now);

        return true;
    }

    /**
     *  받는 사람이 탈퇴하면 계좌번호를 바로 지운다. 정산은 열어 둬 만료로 닫힌다. 이미 지웠으면 false
     */
    public boolean forgetAccount() {
        if(accountNumber == null) return false;

        accountNumber = null;

        return true;
    }

    // ───────────────────────── 조회 ─────────────────────────

    /** payer 가 실제로 부담하는 금액 = 택시비 - 1인당 금액 × 보내는 사람 수 */
    public int payerShare() {
        return payerShare(totalFare, amountPerPerson, transfers.size());
    }

    static int payerShare(int totalFare, int amountPerPerson, int senders) {
        return totalFare - amountPerPerson * senders;
    }

    /** payer 도 보내는 사람도 아니면 NOT_FARE_SPLIT_PARTICIPANT */
    public FareSplitRole roleOf(Long memberId) {
        if(payerId.equals(memberId)) return FareSplitRole.PAYER;

        if(findTransfer(memberId).isPresent()) return FareSplitRole.SENDER;

        throw new BusinessException(MatchingErrorCode.NOT_FARE_SPLIT_PARTICIPANT);
    }

    /** 내 몫 - payer 는 payerShare, 보내는 사람은 1인당 금액 */
    public int amountOf(Long memberId) {
        return roleOf(memberId) == FareSplitRole.PAYER ? payerShare() : amountPerPerson;
    }

    /** 받을 계좌는 열린 정산에서만 보여 준다 */
    public Optional<AccountNumber> receivingAccount() {
        if(status != FareSplitStatus.OPEN) return Optional.empty();

        return Optional.ofNullable(accountNumber);
    }

    /** 1인당 금액을 받을 계좌로 보내는 토스 딥링크. 받을 계좌를 보여 줄 수 없으면 없다 */
    public Optional<String> tossDeeplink() {
        return receivingAccount().map(account -> TossDeeplink.of(amountPerPerson, bank, account));
    }

    public List<Long> senderIds() {
        return transfers.stream().map(FareSplitTransfer::getMemberId).toList();
    }

    /** payer 와 보내는 사람 전원 */
    public List<Long> participantIds() {
        return Stream.concat(Stream.of(payerId), senderIds().stream()).toList();
    }

    public Optional<FareSplitTransfer> findTransfer(Long memberId) {
        return transfers.stream().filter(t -> t.getMemberId().equals(memberId)).findFirst();
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

    public int getAmountPerPerson() {
        return amountPerPerson;
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
