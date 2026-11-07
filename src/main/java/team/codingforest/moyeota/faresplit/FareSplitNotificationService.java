package team.codingforest.moyeota.faresplit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotice;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotifier;
import team.codingforest.moyeota.faresplit.domain.FareSplits;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 *  스케줄러가 부르는 정산 알림 - 송금 재요청(2~4번째), 입금 확인 요청.
 *  언제 차례인지·방해 금지 시간에 어떻게 할지는 FareSplit 이 정하고 여기서는 정산 행 잠금·저장·커밋 후 전송을 묶는다.
 *  조회와 잠금 사이에 취소됐거나 다른 서버가 먼저 처리한 정산은 아무것도 하지 않는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FareSplitNotificationService {
    private final FareSplits fareSplits;
    private final AfterCommitExecutor afterCommitExecutor;
    private final FareSplitNotifier notifier;

    /**
     *  송금 재요청. 방해 금지 시간에는 보내지 않고 차례만 넘긴다 - 나중에 몰아 보내지 않는다
     */
    @Transactional
    public void remindTransfers(Long partyId, Instant now) {
        Optional<FareSplit> found = fareSplits.findByPartyIdForUpdate(partyId);

        if(found.isEmpty()) {
            log.info("송금 재요청 대상 정산이 없어 건너 뜀(취소됨) partyId={}", partyId);
            return;
        }

        FareSplit split = found.get();
        FareSplitNotice notice = split.remindTransfers(now);

        if(!notice.handled()) {
            log.info("송금 재요청 차례가 아니어서 건너 뜀 partyId={}, status={}", partyId, split.getStatus());
            return;
        }

        fareSplits.save(split);

        if(!notice.shouldSend()) {
            log.info("송금 재요청을 보내지 않고 차례만 넘김(방해 금지 시간이거나 모두 보냈다고 누름) partyId={}", partyId);
            return;
        }

        List<Long> recipientIds = notice.recipientIds();
        int amount = split.getAmountPerPerson();
        afterCommitExecutor.execute("fare-split.transfer-reminder", () -> notifier.remindTransfer(partyId, recipientIds, amount));

        log.info("송금 재요청 partyId={}, 받는 사람={}명", partyId, recipientIds.size());
    }

    /**
     *  입금 확인 요청. 방해 금지 시간에는 아무것도 바꾸지 않아 끝난 뒤 다시 고른다
     */
    @Transactional
    public void requestConfirm(Long partyId, Instant now) {
        Optional<FareSplit> found = fareSplits.findByPartyIdForUpdate(partyId);

        if(found.isEmpty()) {
            log.info("입금 확인 요청 대상 정산이 없어 건너 뜀(취소됨) partyId={}", partyId);
            return;
        }

        FareSplit split = found.get();

        if(!split.requestConfirm(now).shouldSend()) {
            log.info("입금 확인 요청 차례가 아니어서 건너 뜀 partyId={}, status={}", partyId, split.getStatus());
            return;
        }

        fareSplits.save(split);

        Long payerId = split.getPayerId();
        afterCommitExecutor.execute("fare-split.confirm-request", () -> notifier.requestConfirm(partyId, payerId));

        log.info("입금 확인 요청 partyId={}, payerId={}", partyId, payerId);
    }
}
