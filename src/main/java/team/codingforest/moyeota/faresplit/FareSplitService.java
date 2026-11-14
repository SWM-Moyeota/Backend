package team.codingforest.moyeota.faresplit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.faresplit.domain.FarePayment;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotifier;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.faresplit.domain.RideRoster;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.enums.TransferStatus;
import team.codingforest.moyeota.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.faresplit.dto.FareSplitRequest;
import team.codingforest.moyeota.faresplit.dto.MyFareSplitListResponse;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.matching.api.dto.PartyRiders;
import team.codingforest.moyeota.user.api.MaskedRealNames;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FareSplitService {
    private final PartyAccess partyAccess;
    private final FareSplits fareSplits;
    private final UserAccess userAccess;
    private final MaskedRealNames maskedRealNames;
    private final AfterCommitExecutor afterCommitExecutor;
    private final FareSplitNotifier notifier;

    /**
     *  방장이 정산을 시작하고 보내는 사람 전원에게 첫 송금 요청을 보냄
     */
    @Transactional
    public FareSplitDetailResponse create(Long partyId, Long payerId, FareSplitRequest request) {
        AccountNumber account = AccountNumber.of(request.accountNumber());
        RideRoster party = findRoster(partyId);

		// 방장이 요청했는지 판별
        party.validateStarter(payerId);
        List<FarePayment> payments = toPayments(party.riderIds(), request.payments());

        FareSplit split = saveNew(FareSplit.create(party, payerId, request.totalFare(), request.bankCode(), account, payments, Instant.now()));

        List<FarePayment> senderPayments = split.senderPayments();
        afterCommitExecutor.execute("fare-split.transfer-request", () -> notifier.requestTransfer(partyId, senderPayments));

        log.info("택시비 정산 시작 partyId={}, payerId={}, totalFare={}, senders={}", partyId, payerId, request.totalFare(), senderPayments.size());

        return toDetail(split, payerId, party.memberIds());
    }

    /**
     *  받는 사람과 보내는 사람만 본다
     */
    @Transactional(readOnly = true)
    public FareSplitDetailResponse getDetail(Long partyId, Long memberId) {
        FareSplit split = fareSplits.findByPartyId(partyId)
                .orElseThrow(() -> new BusinessException(FareSplitErrorCode.FARE_SPLIT_NOT_FOUND));

        // 정산에 없는 사람은 방 정보를 읽기 전에 막는다
        split.validateParticipant(memberId);

        return toDetail(split, memberId, joinOrder(partyId));
    }

    /**
     *  송금자가 보낸 후 방장에게 알림
     */
    @Transactional
    public void markSent(Long partyId, Long senderId) {
        FareSplit split = lock(partyId);

        if (!split.markSent(senderId, Instant.now())) {
            return;
        }

        fareSplits.save(split);

        Long payerId = split.getPayerId();
        afterCommitExecutor.execute("fare-split.confirm-request", () -> notifier.requestConfirm(partyId, payerId));

        log.info("택시비 송금 보냄 partyId={}, senderId={}, payerId={}", partyId, senderId, payerId);
    }

    /**
     *  방장이 senderPublicId 의 송금을 받았다고 확인
	 *  모두 보낸경우 정산 닫힘
     */
    @Transactional
    public void confirmReceived(Long partyId, Long payerId, UUID senderPublicId) {
        FareSplit split = lock(partyId);
        Long senderId = senderIdOf(split, payerId, senderPublicId);

        if (!split.confirmReceived(payerId, senderId, Instant.now())) {
            return;
        }

        fareSplits.save(split);

        if (split.getStatus() == FareSplitStatus.COMPLETED) {
            List<Long> participantIds = split.participantIds();
            afterCommitExecutor.execute("fare-split.completed", () -> notifier.notifyCompleted(partyId, participantIds));
        }

        log.info("택시비 송금 확인 partyId={}, senderId={}, status={}", partyId, senderId, split.getStatus());
    }

    /**
     *  받는 사람이 senderPublicId 의 송금을 아직 못 받았다고 되돌리고 알림
     */
    @Transactional
    public void markNotReceived(Long partyId, Long payerId, UUID senderPublicId) {
        FareSplit split = lock(partyId);
        Long senderId = senderIdOf(split, payerId, senderPublicId);

        split.markNotReceived(payerId, senderId);
        fareSplits.save(split);

        afterCommitExecutor.execute("fare-split.not-received", () -> notifier.notifyNotReceived(partyId, senderId));

        log.info("택시비 송금 못 받음 partyId={}, senderId={}", partyId, senderId);
    }

    /**
     *  받는 사람이 택시비·계좌·사람마다 보낼 금액을 고친다. 아무도 보내기 전인 OPEN 정산만. 응답은 고친 정산 상세(받는 사람 화면)
     */
    @Transactional
    public FareSplitDetailResponse edit(Long partyId, Long payerId, FareSplitRequest request) {
        AccountNumber account = AccountNumber.of(request.accountNumber());

        FareSplit split = lock(partyId);
        split.validatePayer(payerId);
        List<FarePayment> payments = toPayments(split.participantIds(), request.payments());

        split.edit(payerId, request.totalFare(), request.bankCode(), account, payments);
        fareSplits.save(split);

        log.info("택시비 정산 수정 partyId={}, totalFare={}", partyId, request.totalFare());

        return toDetail(split, payerId, joinOrder(partyId));
    }

    /**
     *  내가 보내는 사람인 OPEN 정산 중 내 송금이 status 인 것. NOT_SENT 면 홈 배너다
     */
    @Transactional(readOnly = true)
    public MyFareSplitListResponse findMine(Long memberId, TransferStatus status) {
        List<OpenTransfer> transfers = fareSplits.findOpenTransfers(memberId).stream()
                .filter(transfer -> transfer.status() == status)
                .toList();

        if (transfers.isEmpty()) {
            return MyFareSplitListResponse.of(List.of(), Map.of(), Map.of());
        }

        Map<Long, PartyPlaces> places = partyAccess.findPlaces(transfers.stream().map(OpenTransfer::partyId).toList());
        Map<Long, MemberSummary> payers = userAccess.findMemberSummaries(transfers.stream().map(OpenTransfer::payerId).distinct().toList());

        return MyFareSplitListResponse.of(transfers, places, payers);
    }

    /**
     *  시작하고 1일이 지난 열린 정산을 닫히게함
     */
    @Transactional
    public void expire(Long partyId, Instant now) {
        FareSplit split = lock(partyId);

        if (!split.expire(now)) {
            log.info("택시비 정산 만료 차례가 아니어서 건너 뜀 partyId={}, status={}", partyId, split.getStatus());
            return;
        }

        fareSplits.save(split);

        log.info("택시비 정산 만료 partyId={}", partyId);
    }

    /**
     *  payerId 가 받는 사람인 열린 정산의 계좌번호를 지운다
     */
    @Transactional
    public int forgetPayerAccounts(Long payerId) {
        int cleared = 0;

        for (Long partyId : fareSplits.findOpenByPayer(payerId)) {
            FareSplit split = lock(partyId);

            if (split.forgetAccount()) {
                fareSplits.save(split);
                cleared++;
            }
        }

        return cleared;
    }

    private RideRoster findRoster(Long partyId) {
        return partyAccess.findRiders(partyId)
                .map(riders -> new RideRoster(riders.partyId(), riders.hostId(), riders.finished(), riders.memberIds(), riders.riderIds()))
                .orElseThrow(() -> new BusinessException(FareSplitErrorCode.PARTY_NOT_FOUND));
    }

    private FareSplit saveNew(FareSplit split) {
        try {
            return fareSplits.create(split);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(FareSplitErrorCode.FARE_SPLIT_ALREADY_EXISTS);
        }
    }

    private FareSplit lock(Long partyId) {
        return fareSplits.findByPartyIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(FareSplitErrorCode.FARE_SPLIT_NOT_FOUND));
    }

    /**
     *  경로의 publicId 를 이 정산의 보내는 사람 번호로 변경
     */
    private Long senderIdOf(FareSplit split, Long payerId, UUID senderPublicId) {
        split.validatePayer(payerId);

        return Optional.ofNullable(memberIdsByPublicId(split.senderIds()).get(senderPublicId))
                .orElseThrow(() -> new BusinessException(FareSplitErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND));
    }

    /**
	 * publicId -> userId로 변환
     */
    private List<FarePayment> toPayments(List<Long> memberIds, List<FareSplitRequest.Payment> requests) {
        Map<UUID, Long> memberIdsByPublicId = memberIdsByPublicId(memberIds);

        return requests.stream()
                .map(request -> new FarePayment(memberIdOf(memberIdsByPublicId, request.userPublicId()), request.amount()))
                .toList();
    }

    private Long memberIdOf(Map<UUID, Long> memberIdsByPublicId, UUID publicId) {
        return Optional.ofNullable(memberIdsByPublicId.get(publicId))
                .orElseThrow(() -> new BusinessException(FareSplitErrorCode.PAYMENT_TARGET_MISMATCH));
    }

    private Map<UUID, Long> memberIdsByPublicId(List<Long> memberIds) {
        return userAccess.findMemberSummaries(memberIds).entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getValue().publicId(), Map.Entry::getKey));
    }

    private List<Long> joinOrder(Long partyId) {
        return partyAccess.findRiders(partyId).map(PartyRiders::memberIds).orElse(List.of());
    }

    private FareSplitDetailResponse toDetail(FareSplit split, Long viewerId, List<Long> joinOrder) {
        Map<Long, String> maskedNames = split.isPayer(viewerId) ? maskedRealNames.find(split.senderIds()) : Map.of();

        return FareSplitDetailResponse.of(split, viewerId, joinOrder, userAccess.findMemberSummaries(split.participantIds()), maskedNames);
    }
}
