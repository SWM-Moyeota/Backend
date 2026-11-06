package team.codingforest.moyeota.matching.faresplit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.matching.faresplit.domain.Bank;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplit;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitNotifier;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitRole;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplits;
import team.codingforest.moyeota.matching.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.matching.faresplit.domain.TransferStatus;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.matching.faresplit.dto.MyFareSplitListResponse;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.user.api.MaskedRealNames;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 *  택시비 정산 시작·조회·송금 처리·수정·취소·만료와 받는 사람 탈퇴 정리. 정산 시작은 방 행을 잠가 같은 방의 시작을 줄 세우고,
 *  만든 뒤의 변경은 모두 정산 행을 먼저 잠가 줄 세운다 - 마지막 두 송금을 동시에 확인해도 COMPLETED 로 한 번 닫힌다.
 *  푸시(송금 요청·못 받음·완료)는 커밋된 뒤에만 나간다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FareSplitService {
    private final Parties parties;
    private final FareSplits fareSplits;
    private final UserAccess userAccess;
    private final MaskedRealNames maskedRealNames;
    private final AfterCommitExecutor afterCommitExecutor;
    private final FareSplitNotifier notifier;

    /**
     *  payerId 가 택시비를 다 낸 사람이다. 동승이 끝난 방의 탑승자만, 방마다 한 번 시작할 수 있다.
     *  보내는 사람 전원에게 첫 송금 요청을 보낸다 - 사용자가 누른 일이라 방해 금지 시간에도 보낸다
     */
    @Transactional
    public FareSplitDetailResponse create(Long partyId, Long payerId, int totalFare, Bank bank, String accountNumber) {
        AccountNumber account = AccountNumber.of(accountNumber);

        Party party = parties.findByIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));

        FareSplit split = FareSplit.open(party, payerId, totalFare, bank, account, Instant.now());

        if(fareSplits.existsByPartyId(partyId)) throw new BusinessException(MatchingErrorCode.FARE_SPLIT_ALREADY_EXISTS);

        FareSplit saved;
        try {
            saved = fareSplits.create(split);
        } catch (DataIntegrityViolationException e) {
            // 방 잠금으로 줄을 세우므로 오지 않아야 하는 길 - PK 가 마지막으로 막는다
            throw new BusinessException(MatchingErrorCode.FARE_SPLIT_ALREADY_EXISTS);
        }

        List<Long> senderIds = saved.senderIds();
        int amount = saved.getAmountPerPerson();
        afterCommitExecutor.execute("fare-split.transfer-request", () -> notifier.requestTransfer(partyId, senderIds, amount));

        log.info("택시비 정산 시작 partyId={}, payerId={}, totalFare={}, senders={}", partyId, payerId, totalFare, senderIds.size());

        return toDetail(saved, payerId);
    }

    /**
     *  받는 사람(payer)과 보내는 사람만 본다. 가린 실명은 받는 사람이 볼 때만 싣는다
     */
    @Transactional(readOnly = true)
    public FareSplitDetailResponse getDetail(Long partyId, Long memberId) {
        FareSplit split = fareSplits.findByPartyId(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FARE_SPLIT_NOT_FOUND));

        return toDetail(split, memberId);
    }

    /**
     *  보내는 사람이 보냈다고 누른다. 이미 SENT·CONFIRMED 면 아무것도 바꾸지 않는다
     */
    @Transactional
    public void markSent(Long partyId, Long memberId) {
        FareSplit split = lock(partyId);

        if(!split.markSent(memberId, Instant.now())) return;

        fareSplits.save(split);

        log.info("택시비 송금 보냄 partyId={}, memberId={}", partyId, memberId);
    }

    /**
     *  받는 사람이 senderPublicId 의 송금을 받았다고 확인한다. 이미 CONFIRMED 면 아무것도 바꾸지 않는다.
     *  모두 확인되면 정산이 닫히고 받는 사람과 보내는 사람 전원에게 완료 알림을 보낸다
     */
    @Transactional
    public void confirmReceived(Long partyId, Long payerId, UUID senderPublicId) {
        FareSplit split = lock(partyId);
        split.ensurePayer(payerId);
        Long senderId = senderIdOf(split, senderPublicId);

        if(!split.confirmReceived(payerId, senderId, Instant.now())) return;

        fareSplits.save(split);

        if(split.getStatus() == FareSplitStatus.COMPLETED) {
            List<Long> participantIds = split.participantIds();
            afterCommitExecutor.execute("fare-split.completed", () -> notifier.notifyCompleted(partyId, participantIds));
        }

        log.info("택시비 송금 확인 partyId={}, senderId={}, status={}", partyId, senderId, split.getStatus());
    }

    /**
     *  받는 사람이 senderPublicId 의 송금을 아직 못 받았다고 되돌리고(SENT → NOT_SENT) 그 보내는 사람에게 알린다
     */
    @Transactional
    public void markNotReceived(Long partyId, Long payerId, UUID senderPublicId) {
        FareSplit split = lock(partyId);
        split.ensurePayer(payerId);
        Long senderId = senderIdOf(split, senderPublicId);

        split.markNotReceived(payerId, senderId);
        fareSplits.save(split);

        afterCommitExecutor.execute("fare-split.not-received", () -> notifier.notifyNotReceived(partyId, senderId));

        log.info("택시비 송금 못 받음 partyId={}, senderId={}", partyId, senderId);
    }

    /**
     *  받는 사람이 택시비·계좌를 고친다. 아무도 보내기 전인 OPEN 정산만. 응답은 고친 정산 상세(받는 사람 화면)
     */
    @Transactional
    public FareSplitDetailResponse edit(Long partyId, Long payerId, int totalFare, Bank bank, String accountNumber) {
        AccountNumber account = AccountNumber.of(accountNumber);

        FareSplit split = lock(partyId);
        split.edit(payerId, totalFare, bank, account);
        fareSplits.save(split);

        log.info("택시비 정산 수정 partyId={}, totalFare={}", partyId, totalFare);

        return toDetail(split, payerId);
    }

    /**
     *  받는 사람이 취소한다. 아무도 보내기 전인 OPEN 정산만. 정산과 송금 행을 지운다
     */
    @Transactional
    public void cancel(Long partyId, Long payerId) {
        FareSplit split = lock(partyId);
        split.ensureCancelable(payerId);
        fareSplits.delete(partyId);

        log.info("택시비 정산 취소 partyId={}, payerId={}", partyId, payerId);
    }

    /**
     *  내가 보내는 사람인 OPEN 정산 중 내 송금이 status 인 것. NOT_SENT 면 홈 배너다
     */
    @Transactional(readOnly = true)
    public MyFareSplitListResponse findMine(Long memberId, TransferStatus status) {
        List<OpenTransfer> transfers = fareSplits.findOpenTransfers(memberId).stream()
                .filter(t -> t.status() == status)
                .toList();

        if(transfers.isEmpty()) return MyFareSplitListResponse.of(List.of(), Map.of());

        Map<Long, MemberSummary> payers = userAccess.findMemberSummaries(transfers.stream().map(OpenTransfer::payerId).distinct().toList());

        return MyFareSplitListResponse.of(transfers, payers);
    }

    /**
     *  시작하고 7일이 지난 OPEN 정산을 EXPIRED 로 닫고 계좌번호를 지운다(스케줄러). 조회와 잠금 사이에 닫히거나 취소된 정산은 그대로 둔다
     */
    @Transactional
    public void expire(Long partyId, Instant now) {
        Optional<FareSplit> found = fareSplits.findByPartyIdForUpdate(partyId);

        if(found.isEmpty() || !found.get().expire(now)) {
            log.info("택시비 정산 만료 차례가 아니어서 건너 뜀 partyId={}", partyId);
            return;
        }

        fareSplits.save(found.get());

        log.info("택시비 정산 만료 partyId={}", partyId);
    }

    /**
     *  payerId 가 받는 사람인 열린 정산의 계좌번호를 지운다(탈퇴). 이미 지운 정산은 그대로라 다시 불러도 된다. 지운 정산 수를 돌려준다
     */
    @Transactional
    public int forgetPayerAccounts(Long payerId) {
        int cleared = 0;

        for(Long partyId : fareSplits.findOpenByPayer(payerId)) {
            Optional<FareSplit> split = fareSplits.findByPartyIdForUpdate(partyId);

            if(split.isPresent() && split.get().forgetAccount()) {
                fareSplits.save(split.get());
                cleared++;
            }
        }

        return cleared;
    }

    private FareSplit lock(Long partyId) {
        return fareSplits.findByPartyIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FARE_SPLIT_NOT_FOUND));
    }

    // 경로의 publicId 를 이 정산의 보내는 사람 번호로 바꾼다. 탈퇴한 사람도 publicId 는 남는다
    private Long senderIdOf(FareSplit split, UUID publicId) {
        return userAccess.findMemberSummaries(split.senderIds()).entrySet().stream()
                .filter(e -> publicId.equals(e.getValue().publicId()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FARE_SPLIT_TRANSFER_NOT_FOUND));
    }

    private FareSplitDetailResponse toDetail(FareSplit split, Long viewerId) {
        FareSplitRole role = split.roleOf(viewerId);

        Map<Long, String> maskedNames = role == FareSplitRole.PAYER ? maskedRealNames.find(split.senderIds()) : Map.of();

        return FareSplitDetailResponse.of(split, viewerId, userAccess.findMemberSummaries(split.participantIds()), maskedNames);
    }
}
