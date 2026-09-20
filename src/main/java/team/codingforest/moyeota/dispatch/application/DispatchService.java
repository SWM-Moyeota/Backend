package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.application.event.CallAcceptedEvent;
import team.codingforest.moyeota.dispatch.application.event.CallOpenedEvent;
import team.codingforest.moyeota.dispatch.domain.CallCandidates;
import team.codingforest.moyeota.dispatch.domain.DriverLocations;
import team.codingforest.moyeota.dispatch.domain.exception.DispatchErrorCode;
import team.codingforest.moyeota.driver.api.DriverAccess;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.time.Duration;
import java.util.List;

/**
 *  트랜잭션 안에는 DB 판단과 상태 변경만 둔다. Redis 후보 명단 갱신과 FCM 알림은
 *  이벤트로 넘겨 커밋 뒤에 {@link DispatchEventListener} 가 처리한다.
 */
// TODO 예외처리 작성해야함
@Service
@Slf4j
@RequiredArgsConstructor
public class DispatchService {

    private final PartyAccess partyAccess;
    private final DriverAccess driverAccess;
    private final DriverLocations driverLocations;
    private final CallCandidates callCandidates;
    private final ApplicationEventPublisher eventPublisher;

    private static final int INITIAL_RADIUS_METERS = 1000;
    private static final int RADIUS_STEP_METERS = 500;
    private static final int MAX_RADIUS_METERS = 3000;
    public static final Duration MATCHING_TIMEOUT = Duration.ofMinutes(3);

    /**
     *      매칭방을 기준으로 3km 이내의 기사들을 찾고 콜 뿌리기
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void dispatch(Long partyId) {
        attempt(partyId, INITIAL_RADIUS_METERS);
    }

    /**
     *      콜 수락 - 기사 배정만 커밋하고, 후보 정리·탈락 기사 마감 통지는 커밋 후 리스너가 한다.
     *      assignDriver 가 방 행에 FOR UPDATE 락을 잡으므로 여기서 하는 일이 곧 락 보유 시간이다.
     */
    @Transactional
    public void acceptCall(Long partyId, Long driverId) {
        if(!callCandidates.contains(partyId, driverId)) throw new BusinessException(DispatchErrorCode.CALL_CLOSED);

        if(!driverAccess.canReceiveCalls(driverId)) throw new BusinessException(DispatchErrorCode.DRIVER_CANNOT_RECEIVE);

        if(partyAccess.hasOngoingRide(driverId)) throw new BusinessException(DispatchErrorCode.DRIVER_ALREADY_RIDING);

        // 현재 모델에서 계정을 하나로 공유하기 때문에 기사가 기사의 매칭방에 있는 예외 방어
        driverAccess.findUserId(driverId)
                .filter(userId -> partyAccess.hasMemberOnParty(partyId, userId))
                .ifPresent(userId -> { throw new BusinessException(DispatchErrorCode.SELF_DISPATCH_NOT_ALLOWED); });

        partyAccess.assignDriver(partyId, driverId);

        List<Long> losers = callCandidates.findAll(partyId).stream()
                .filter(id -> !id.equals(driverId))
                .toList();

        eventPublisher.publishEvent(new CallAcceptedEvent(partyId, driverId, losers));

        log.info("콜 수락 partyId={}, driverId={}, 커밋 후 마감 통지 예정={}명", partyId, driverId, losers.size());
    }

    @Transactional
    public void rejectCall(Long partyId, Long driverId) {
        if(!callCandidates.contains(partyId, driverId)) throw new BusinessException(DispatchErrorCode.CALL_CLOSED);

        long remaining = callCandidates.remove(partyId, driverId);
        callCandidates.markRejected(partyId, driverId);

        log.info("콜 거절 partyId={}, driverId={}, 남은 후보={}명", partyId, driverId, remaining);
    }

    /**
     *      반경 안의 신규 기사를 고른다. DB 는 읽기만 하고, 후보 등록과 콜 알림은 커밋 후 리스너가 한다.
     */
    @Transactional(readOnly = true)
    public void attempt(Long partyId, int radiusMeters) {
        PartySummary party = partyAccess.findSummary(partyId)
                .orElseThrow(() -> new BusinessException(DispatchErrorCode.PARTY_NOT_FOUND));

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

        eventPublisher.publishEvent(new CallOpenedEvent(partyId, fresh, party));

        log.info("기사 탐색 partyId={}, radius={}m, 신규={}명, 누적={}명 - 커밋 후 호출", partyId, radiusMeters, fresh.size(), already.size() + fresh.size());
    }

    public int radiusFor(Duration elapsed) {
        long steps = elapsed.getSeconds() / 30;
        return (int) Math.min(MAX_RADIUS_METERS, INITIAL_RADIUS_METERS + steps * RADIUS_STEP_METERS);
    }

    @Transactional(readOnly = true)
    public PartySummary getDetailRoom(Long driverId, Long partyId) {
        if(!isCallOpen(partyId, driverId)) throw new BusinessException(DispatchErrorCode.CALL_CLOSED);

        return partyAccess.findSummary(partyId)
                .orElseThrow(() -> new BusinessException(DispatchErrorCode.PARTY_NOT_FOUND));
    }

    public boolean isCallOpen(Long partyId, Long driverId) {
        return callCandidates.contains(partyId, driverId);
    }
}
