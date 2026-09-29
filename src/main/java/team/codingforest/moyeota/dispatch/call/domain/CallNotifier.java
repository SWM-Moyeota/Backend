package team.codingforest.moyeota.dispatch.call.domain;

import team.codingforest.moyeota.matching.api.dto.PartySummary;

import java.util.List;

public interface CallNotifier {
    void notifyCall(List<Long>driverIds, PartySummary party);
    void notifyCallClosed(List<Long> driverIds, Long partyId);
}
