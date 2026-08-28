package team.codingforest.moyeota.dispatch.application;

import team.codingforest.moyeota.matching.api.MatchingTarget;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartyChatSummary;
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
    final List<MatchingTarget> matchingTargets = new ArrayList<>();
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
    public void failMatching(Long partyId) {
        if(cancelRejected.contains(partyId) || assignedDriverId != null) throw new IllegalArgumentException("이미 기사가 배정된 방입니다.");
        matchingCanceled.add(partyId);
    }

    @Override
    public List<MatchingTarget> findMatchingTargets() {
        return List.copyOf(matchingTargets);
    }

    // ───── 운행 흐름 - 도메인 가드(배정 기사만, 순서 위반 불가)를 흉내낸다 ─────

    boolean rideStarted;
    Integer completedFare;

    @Override
    public void startRide(Long partyId, Long driverId) {
        ensureAssignedDriver(driverId);
        if(rideStarted) throw new IllegalArgumentException("탑승 대기 상태가 아닙니다");
        rideStarted = true;
    }

    @Override
    public void completeRide(Long partyId, Long driverId, int fare) {
        ensureAssignedDriver(driverId);
        if(!rideStarted) throw new IllegalArgumentException("운행중이 아닙니다.");
        completedFare = fare;
    }

    @Override
    public boolean isAwaitingPickup(Long partyId, Long driverId) {
        return driverId.equals(assignedDriverId) && !rideStarted && completedFare == null;
    }

    @Override
    public boolean hasOngoingRide(Long driverId) {
        // 배정~운행 구간만 true, 운행이 끝나면(FINISHED) 자동으로 자유 - 실제 쿼리 의미 그대로
        return driverId.equals(assignedDriverId) && completedFare == null;
    }

    @Override
    public PartyChatSummary findChatSummary(Long partyId) {
        PartySummary summary = summaries.get(partyId);
        if(summary == null) throw new IllegalArgumentException("존재하지 않는 방입니다.");
        return new PartyChatSummary(partyId, List.of(), summary.departure(), summary.destination());
    }

    private void ensureAssignedDriver(Long driverId) {
        if(assignedDriverId == null || !assignedDriverId.equals(driverId)) throw new IllegalArgumentException("이 방에 배정된 기사가 아닙니다.");
    }
}
