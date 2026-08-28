package team.codingforest.moyeota.dispatch.application;

import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 *  방 요약을 돌려주고 배정/되돌림을 기록하는 가짜.
 *  assignDriver/cancelMatching 은 실제 도메인 가드(배정 후 불가)를 흉내낸다.
 */
class FakePartyAccess implements PartyAccess {
    private final Map<Long, PartySummary> summaries = new HashMap<>();
    List<Long> matchingIds = new ArrayList<>();
    Long assignedDriverId;
    final List<Long> matchingCanceled = new ArrayList<>();
    final Set<Long> cancelRejected = new HashSet<>();   // cancelMatching 이 예외를 던질 방 (스윕 중 수락 경합 재현)

    FakePartyAccess(PartySummary... summaries) {
        for(PartySummary s : summaries) {
            this.summaries.put(s.id(), s);
        }
    }

    @Override
    public Optional<PartySummary> findSummary(Long partyId) {
        return Optional.ofNullable(summaries.get(partyId));
    }

    @Override
    public void assignDriver(Long partyId, Long driverId) {
        if(assignedDriverId != null) throw new IllegalArgumentException("이미 기사가 배정된 방입니다.");
        assignedDriverId = driverId;
    }

    @Override
    public void cancelMatching(Long partyId) {
        if(cancelRejected.contains(partyId) || assignedDriverId != null) throw new IllegalArgumentException("이미 기사가 배정된 방입니다.");
        matchingCanceled.add(partyId);
    }

    @Override
    public List<Long> findMatchingIds() {
        return List.copyOf(matchingIds);
    }
}
