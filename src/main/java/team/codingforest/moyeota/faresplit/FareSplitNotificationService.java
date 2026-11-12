package team.codingforest.moyeota.faresplit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.faresplit.domain.FarePayment;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotice;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotifier;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import java.time.Instant;
import java.util.List;

/**
 *  스케줄러가 부르는 정산 알림 송금 재요청.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FareSplitNotificationService {
    private final FareSplits fareSplits;
    private final AfterCommitExecutor afterCommitExecutor;
    private final FareSplitNotifier notifier;

    @Transactional
    public void remindTransfers(Long partyId, Instant now) {
        FareSplit split = fareSplits.findByPartyIdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(FareSplitErrorCode.FARE_SPLIT_NOT_FOUND));

        FareSplitNotice notice = split.remindTransfers(now);

        if (!notice.shouldSend()) {
            log.info("송금 재요청 차례가 아니어서 건너 뜀 partyId={}, status={}", partyId, split.getStatus());
            return;
        }

        fareSplits.save(split);

        List<FarePayment> payments = notice.payments();
        afterCommitExecutor.execute("fare-split.transfer-reminder", () -> notifier.remindTransfer(partyId, payments));

        log.info("송금 재요청 partyId={}, 받는 사람={}명", partyId, payments.size());
    }
}
