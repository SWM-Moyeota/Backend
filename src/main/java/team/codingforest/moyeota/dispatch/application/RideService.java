package team.codingforest.moyeota.dispatch.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.domain.exception.DispatchErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.dispatch.application.dto.DriverLocationResponse;
import team.codingforest.moyeota.dispatch.domain.DriverLocations;
import team.codingforest.moyeota.dispatch.domain.DriverPosition;
import team.codingforest.moyeota.dispatch.domain.PassengerNotifier;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartySummary;

@Service
@Slf4j
@RequiredArgsConstructor
public class RideService {
    private final PartyAccess partyAccess;
    private final PassengerNotifier passengerNotifier;
    private final DriverLocations driverLocations;

    /**
     *      기사 도착 - 승객 알림
     */
    public void arrive(Long partyId, Long driverId) {
        if(!partyAccess.isAwaitingPickup(partyId, driverId)) throw new BusinessException(DispatchErrorCode.NOT_AWAITING_PICKUP);

        passengerNotifier.notifyDriverArrived(partyId);

        log.info("기사 도착 통보 partyId={}, driverId={}", partyId, driverId);
    }

    /**
     *      탑승 완료 - 운행 시작 (DRIVER_ASSIGNED → IN_RIDE)
     */
    public void board(Long partyId, Long driverId) {
        partyAccess.startRide(partyId, driverId);

        log.info("운행 시작 partyId={}, driverId={}", partyId, driverId);
    }

    /**
     *      운행 종료 - 요금 입력 (IN_RIDE → FINISHED)
     */
    public void complete(Long partyId, Long driverId, int fare) {
        partyAccess.completeRide(partyId, driverId, fare);

        log.info("운행 종료 partyId={}, driverId={}, fare={}", partyId, driverId, fare);
    }

    /**
     *      승객이 오는 기사의 현재 위치를 조회
     */
    public DriverLocationResponse driverLocation(Long partyId, Long memberId) {
        if(!partyAccess.hasMemberOnParty(memberId, partyId)) throw new BusinessException(DispatchErrorCode.NOT_PARTY_MEMBER);

        PartySummary summary = partyAccess.findSummary(partyId)
                .orElseThrow(() -> new BusinessException(DispatchErrorCode.PARTY_NOT_FOUND));

        if(summary.driverId() == null) throw new BusinessException(DispatchErrorCode.DRIVER_NOT_ASSIGNED);

        DriverPosition position = driverLocations.getLocationDriver(summary.driverId())
                .orElseThrow(() -> new BusinessException(DispatchErrorCode.DRIVER_LOCATION_UNAVAILABLE));

        return new DriverLocationResponse(position.longitude(), position.latitude());
    }
}