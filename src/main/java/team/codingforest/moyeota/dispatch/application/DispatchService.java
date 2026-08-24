package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.dispatch.domain.CallCandidates;
import team.codingforest.moyeota.dispatch.domain.CallNotifier;
import team.codingforest.moyeota.dispatch.domain.DriverLocations;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.List;

// TODO 예외처리 작성해야함
@Service
@Slf4j
@RequiredArgsConstructor
public class DispatchService {
    private final PartyAccess partyAccess;
    private final DriverAccess driverAccess;
    private final DriverLocations driverLocations;
    private final CallNotifier callNotifier;
    private final CallCandidates callCandidates;

    private static final int searchRadiusMeters = 3000;

    /**
     *      매칭방을 기준으로 3km 이내의 기사들을 찾고 콜 뿌리기
     */
    public void dispatch(Long partyId) {
        PartySummary party = partyAccess.findSummary(partyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 방이 없음"));

        List<Long> nearbyIds = driverLocations.findNearby(party.departureLatitude(), party.departureLongitude(), searchRadiusMeters);

        List<Long> candidates = nearbyIds.stream()
                .filter(driverAccess::canReceiveCalls)
                .toList();

        if(candidates.isEmpty()) {
            log.warn("호출 가능한 기사 없음 partyId={}, radius={}m", partyId, searchRadiusMeters);
            partyAccess.cancelMatching(partyId);
            return;
        }

        callCandidates.register(partyId, candidates);
        callNotifier.notifyCall(candidates, party);

        log.info("기사 호출 완료 partyId={}, 후보={}명", partyId, candidates.size());
    }

    @Transactional
    public void acceptCall(Long partyId, Long driverId) {
        if(!callCandidates.contains(partyId, driverId)) throw new IllegalArgumentException("호출받지 않은 콜입니다.");

        if(!driverAccess.canReceiveCalls(driverId)) throw new IllegalArgumentException("콜을 받을 수 없는 기사입니다.");

        partyAccess.assignDriver(partyId, driverId);

        List<Long> losers = callCandidates.findAll(partyId).stream()
                .filter(id -> !id.equals(driverId))
                .toList();

        callCandidates.clear(partyId);
        driverLocations.remove(driverId);
        callNotifier.notifyCallClosed(losers, partyId);

        log.info("콜 수락 partyId={}, driverId={}, 콜 알림 취소된 사람 = {}명", partyId, driverId, losers.size());
    }

    public void rejectCall(Long partyId, Long driverId) {
        if(!callCandidates.contains(partyId, driverId)) throw new IllegalArgumentException("호출받지 않은 콜입니다.");

        long remaining = callCandidates.remove(partyId, driverId);

        log.info("콜 거절 partyId={}, driverId={}, 남은 후보={}명", partyId, driverId, remaining);

        if(remaining == 0) {
            partyAccess.cancelMatching(partyId);

            log.warn("전원 거절로 매칭 복귀 partyId={}", partyId);
        }
    }

    public boolean isCallOpen(Long partyId, Long driverId) {
        return callCandidates.contains(partyId, driverId);
    }
}
