package team.codingforest.moyeota.matching.faresplit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.matching.faresplit.domain.Bank;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplit;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitRole;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplits;
import team.codingforest.moyeota.matching.faresplit.dto.FareSplitDetailResponse;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.user.api.MaskedRealNames;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Instant;
import java.util.Map;

/**
 *  택시비 정산 시작·조회. 정산 시작은 방 행을 잠가 같은 방의 시작을 줄 세운다 - 두 탑승자가 동시에 눌러도 하나만 만들어진다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FareSplitService {
    private final Parties parties;
    private final FareSplits fareSplits;
    private final UserAccess userAccess;
    private final MaskedRealNames maskedRealNames;

    /**
     *  payerId 가 택시비를 다 낸 사람이다. 동승이 끝난 방의 탑승자만, 방마다 한 번 시작할 수 있다
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

        log.info("택시비 정산 시작 partyId={}, payerId={}, totalFare={}, senders={}", partyId, payerId, totalFare, saved.senderIds().size());

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

    private FareSplitDetailResponse toDetail(FareSplit split, Long viewerId) {
        FareSplitRole role = split.roleOf(viewerId);

        Map<Long, String> maskedNames = role == FareSplitRole.PAYER ? maskedRealNames.find(split.senderIds()) : Map.of();

        return FareSplitDetailResponse.of(split, viewerId, userAccess.findMemberSummaries(split.participantIds()), maskedNames);
    }
}
