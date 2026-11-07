package team.codingforest.moyeota.faresplit.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotifier;

import java.util.List;

/** FCM 설정이 없는 환경(로컬·테스트)의 대체 구현 - 보내는 대신 로그만 남긴다 */
@Slf4j
@Component
public class LoggingFareSplitNotifier implements FareSplitNotifier {

    @Override
    public void requestTransfer(Long partyId, List<Long> senderIds, int amount) {
        log.info("[택시비 송금 요청] partyId={}, 받는 사람={}명, amount={}", partyId, senderIds.size(), amount);
    }

    @Override
    public void remindTransfer(Long partyId, List<Long> senderIds, int amount) {
        log.info("[택시비 송금 재요청] partyId={}, 받는 사람={}명, amount={}", partyId, senderIds.size(), amount);
    }

    @Override
    public void requestConfirm(Long partyId, Long payerId) {
        log.info("[입금 확인 요청] partyId={}, payerId={}", partyId, payerId);
    }

    @Override
    public void notifyNotReceived(Long partyId, Long senderId) {
        log.info("[못 받음 알림] partyId={}, senderId={}", partyId, senderId);
    }

    @Override
    public void notifyCompleted(Long partyId, List<Long> participantIds) {
        log.info("[정산 완료 알림] partyId={}, 받는 사람={}명", partyId, participantIds.size());
    }
}
