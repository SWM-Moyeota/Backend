package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.dispatch.domain.PassengerNotifier;
import team.codingforest.moyeota.matching.api.PartyAccess;

@Service
@Slf4j
@RequiredArgsConstructor
public class RideService {
    private final PartyAccess partyAccess;
    private final PassengerNotifier passengerNotifier;

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
}