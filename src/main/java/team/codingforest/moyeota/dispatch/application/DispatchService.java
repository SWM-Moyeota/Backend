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

import java.time.Duration;
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

    private static final int INITIAL_RADIUS_METERS = 1000;
    private static final int RADIUS_STEP_METERS = 500;
    private static final int MAX_RADIUS_METERS = 3000;
    public static final Duration MATCHING_TIMEOUT = Duration.ofMinutes(3);

    /**
     *      매칭방을 기준으로 3km 이내의 기사들을 찾고 콜 뿌리기
     */
    public void dispatch(Long partyId) {
        attempt(partyId, INITIAL_RADIUS_METERS);
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
        callCandidates.markRejected(partyId, driverId);

        log.info("콜 거절 partyId={}, driverId={}, 남은 후보={}명", partyId, driverId, remaining);
    }

    public void attempt(Long partyId, int radiusMeters) {
        PartySummary party = partyAccess.findSummary(partyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 방이 없음"));

        List<Long> nearbyIds = driverLocations.findNearby(party.departureLatitude(), party.departureLongitude(), radiusMeters);

        List<Long> already = callCandidates.findAll(partyId);
        List<Long> rejected = callCandidates.findRejected(partyId);

        List<Long> fresh = nearbyIds.stream()
                .filter(id -> !already.contains(id))
                .filter(id -> !rejected.contains(id))   // 거절한 기사에게 콜을 다시 보내지 않는다
                .filter(driverAccess::canReceiveCalls)
                .toList();

        if(fresh.isEmpty()) {
            log.info("신규 후보 없음 partyId={}, radius={}m, 기존 후보={}명", partyId, radiusMeters, already.size());
            return;
        }

        callCandidates.add(partyId, fresh);
        callNotifier.notifyCall(fresh, party);

        log.info("기사 호출 partyId={}, radius={}m, 신규={}명, 누적={}명", partyId, radiusMeters, fresh.size(), already.size() + fresh.size());
    }

    public int radiusFor(Duration elapsed) {
        long steps = elapsed.getSeconds() / 30;
        return (int) Math.min(MAX_RADIUS_METERS, INITIAL_RADIUS_METERS + steps * RADIUS_STEP_METERS);
    }

    public PartySummary getDetailRoom(Long driverId, Long partyId) {
        if(!isCallOpen(partyId, driverId)) throw new IllegalArgumentException("해당 콜을 받지 못했습니다.");

        return partyAccess.findSummary(partyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않은 방입니다."));
    }

    public boolean isCallOpen(Long partyId, Long driverId) {
        return callCandidates.contains(partyId, driverId);
    }
}
