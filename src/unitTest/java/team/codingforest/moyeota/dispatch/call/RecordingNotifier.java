package team.codingforest.moyeota.dispatch.call;

import team.codingforest.moyeota.dispatch.call.domain.CallNotifier;
import team.codingforest.moyeota.matching.api.dto.PartySummary;

import java.util.ArrayList;
import java.util.List;

/**
 *  누구에게 몇 번 알림/마감이 갔는지 기록만 하는 가짜
 */
class RecordingNotifier implements CallNotifier {
    final List<Long> notifiedDrivers = new ArrayList<>();
    final List<Long> closedDrivers = new ArrayList<>();
    int callCount = 0;

    @Override
    public void notifyCall(List<Long> driverIds, PartySummary party) {
        callCount++;
        notifiedDrivers.addAll(driverIds);
    }

    @Override
    public void notifyCallClosed(List<Long> driverIds, Long partyId) {
        closedDrivers.addAll(driverIds);
    }
}
