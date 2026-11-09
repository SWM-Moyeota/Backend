package team.codingforest.moyeota.faresplit.domain;

import java.util.List;

/**
 *  택시비 정산 푸시. 받을 사람은 부르는 쪽이 정하고, 여기서는 보내기만 한다
 */
public interface FareSplitNotifier {

    // 첫번째 송금 요청
    void requestTransfer(Long partyId, List<Long> senderIds, int amount);

    // 송금 재 요청(탑승자들이 안보냈을 때)
    void remindTransfer(Long partyId, List<Long> senderIds, int amount);

    // 입금 확인 요청
    void requestConfirm(Long partyId, Long payerId);

    // 송금한 사람이 보냈다고 알림을 보냈지만 송금자는 받지 못했을때 재요청 알림
    void notifyNotReceived(Long partyId, Long senderId);

    // 정산 완료 알림
    void notifyCompleted(Long partyId, List<Long> participantIds);
}
