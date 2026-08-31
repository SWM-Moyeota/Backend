package team.codingforest.moyeota.dispatch.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.domain.exception.DispatchErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.dispatch.domain.DriverLocations;
import team.codingforest.moyeota.driver.api.DriverAccess;

@Service
@Slf4j
@RequiredArgsConstructor
public class DriverLocationService {
    private final DriverLocations driverLocations;
    private final DriverAccess driverAccess;


    /**
     *  기사 운행 시작
     */
    public void goOnline(Long driverId, double latitude, double longitude) {
        if(!driverAccess.canReceiveCalls(driverId)) {
            throw new BusinessException(DispatchErrorCode.DRIVER_CANNOT_RECEIVE);
        }

        driverLocations.update(driverId, latitude, longitude);

        log.info("기사 운행 시작 driverId={}", driverId);
    }

    /**
     *  주기적 위치 보고
     */
    public void report(Long driverId, double latitude, double longitude) {
        driverLocations.update(driverId, latitude, longitude);
    }


    /**
     *  기사 운행 종료
     */
    public void goOffline(Long driverId) {
        driverLocations.remove(driverId);

        log.info("기사 운행 종료 driverId={}", driverId);
    }
}
