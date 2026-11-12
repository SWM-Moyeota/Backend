package team.codingforest.moyeota.faresplit.domain;

import java.util.List;

public interface FareSplitNotifier {

	// 정산 시작했을때 요청 알림
	void requestTransfer(Long partyId, List<FarePayment> payments);

	// 4시간마다 나가는 재요청 알림
	void remindTransfer(Long partyId, List<FarePayment> payments);

	void requestConfirm(Long partyId, Long payerId);

	void notifyNotReceived(Long partyId, Long senderId);

	void notifyCompleted(Long partyId, List<Long> participantIds);
}
