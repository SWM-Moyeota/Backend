package team.codingforest.moyeota.faresplit.domain;

import java.util.List;

/**
 *  택시비 정산 푸시. 받을 사람은 부르는 쪽이 정하고, 여기서는 보내기만 한다
 */
public interface FareSplitNotifier {

    /** 송금 요청(1번째) - 정산을 시작할 때 보내는 사람 전원. amount 는 1인당 금액 */
    void requestTransfer(Long partyId, List<Long> senderIds, int amount);

    /** 송금 재요청(마지막 요청 4시간 뒤) - 아직 안 보낸 보내는 사람 */
    void remindTransfer(Long partyId, List<Long> senderIds, int amount);

    /** 입금 확인 요청 - 보냈다고 누른 지 4시간이 지나도 확인하지 않은 받는 사람 */
    void requestConfirm(Long partyId, Long payerId);

    /** 못 받았어요 - 받는 사람이 되돌린 송금의 보내는 사람 */
    void notifyNotReceived(Long partyId, Long senderId);

    /** 정산 완료 - 받는 사람과 보내는 사람 전원 */
    void notifyCompleted(Long partyId, List<Long> participantIds);
}
