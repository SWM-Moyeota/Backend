package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    private static final int searchRadiusMeters = 3000;

    public void dispatch(Long partyId) {
        PartySummary party = partyAccess.findSummary(partyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 방이 없음"));

        List<Long> nearbyIds = driverLocations.findNearby(party.departureLatitude(), party.departureLongitude(), searchRadiusMeters);

        List<Long> candidates = nearbyIds.stream()
                .filter(driverAccess::canReceiveCalls)
                .toList();

        if(candidates.isEmpty()) {
            log.warn("호출 가능한 기사 없음 partyId={}, radius={}m", partyId, searchRadiusMeters);
            return;
        }

        callNotifier.notifyCall(candidates, party);

        log.info("기사 호출 완료 partyId={}, 후보={}명", partyId, candidates.size());
    }
}
