package team.codingforest.moyeota.dispatch.application;

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
        if(!partyAccess.isAwaitingPickup(partyId, driverId)) throw new IllegalArgumentException("픽업 대기 중인 방이 아닙니다.");

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
        if(!partyAccess.hasMemberOnParty(memberId, partyId)) throw new IllegalArgumentException("해당 방에 참여하고 있지 않습니다.");

        PartySummary summary = partyAccess.findSummary(partyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방입니다."));

        if(summary.driverId() == null) throw new IllegalArgumentException("아직 기사가 배정되지 않았습니다.");

        DriverPosition position = driverLocations.getLocationDriver(summary.driverId())
                .orElseThrow(() -> new IllegalArgumentException("현재 기사님의 위치를 확인할 수 없습니다."));

        return new DriverLocationResponse(position.longitude(), position.latitude());
    }
}