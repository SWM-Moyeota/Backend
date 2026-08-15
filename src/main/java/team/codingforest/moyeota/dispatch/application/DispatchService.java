package team.codingforest.moyeota.dispatch.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.matching.domain.Parties;
import team.codingforest.moyeota.matching.domain.Party;

// TODO 예외처리 작성해야함
@Service
@Slf4j
@RequiredArgsConstructor
public class DispatchService {
    private final Parties parties;
    private final Drivers drivers;
    private final DriverLocations driverLocations;
    private final CallNotifier callNotifier;

    private static final int searchRadiusMeters = 3000;

    public void dispatch(Long partyId) {
        Party party  = parties.findById(partyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 방이 없음"));


    }
}
