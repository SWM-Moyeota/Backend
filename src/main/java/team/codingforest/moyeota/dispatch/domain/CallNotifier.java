package team.codingforest.moyeota.dispatch.domain;

import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.List;

public interface CallNotifier {
    void notifyCall(List<Long>driverIds, PartySummary party);
}
