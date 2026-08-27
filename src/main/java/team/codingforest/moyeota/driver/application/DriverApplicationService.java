package team.codingforest.moyeota.driver.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.driver.application.dto.DriverResult;
import team.codingforest.moyeota.driver.application.dto.RegisterDriverCommand;
import team.codingforest.moyeota.driver.application.dto.RegisterVehicleCommand;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.driver.domain.Vehicle;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.PartySummary;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class DriverApplicationService {
    private final Drivers service;

    @Transactional
    public DriverResult register(RegisterDriverCommand command) {
        validateNotRegistered(command.userId());

        Driver driver = Driver.register(command.userId(), command.qualificationNumber(), new BankAccount(command.bankName(), command.accountNumber()));

        DriverResult result = DriverResult.from(service.save(driver));

        log.info("기사 등록 신청 driverId={}, userId={}", result.id(), result.userId());

        return result;
    }

    @Transactional
    public void verify(Long driverId) {
        Driver driver = getDriver(driverId);

        driver.verify(Instant.now());
        service.save(driver);

        log.info("기사 자격 검증 완료 driverId={}", driverId);
    }

    @Transactional
    public void registerVehicle(RegisterVehicleCommand command) {
        Driver driver = getDriver(command.id());

        driver.registerVehicle(new Vehicle(command.seats(), command.plateNumber(), command.type()));
        service.save(driver);

        log.info("차량 등록 driverId={}, plateNumber={}", command.id(), command.plateNumber());
    }

    @Transactional
    public void enableCall(Long driverId) {
        Driver driver = getDriver(driverId);

        driver.enableCall();
        service.save(driver);

        log.info("콜 수신 켬 driverId={}", driverId);
    }

    @Transactional
    public void registerFcmToken(Long driverId, String token) {
        Driver driver = getDriver(driverId);

        driver.registerFcmToken(token);
        service.save(driver);

        log.info("FCM 토큰 등록 driverId={}", driverId);
    }

    @Transactional
    public void removeFcmToken(Long driverId) {
        Driver driver = getDriver(driverId);

        driver.clearFcmToken();

        service.save(driver);

        log.info("FCM 토큰 삭제 driverId={}", driverId);
    }

    @Transactional
    public void disableCall(Long driverId) {
        Driver driver = getDriver(driverId);

        driver.disableCall();
        service.save(driver);

        log.info("콜 수신 끔 driverId={}", driverId);
    }

    @Transactional(readOnly = true)
    public DriverResult getByUserId(Long userId) {
        return service.findByUserId(userId)
                .map(DriverResult::from)
                .orElseThrow(() -> new IllegalArgumentException("기사로 등록되지 않은 유저입니다."));
    }

    private Driver getDriver(Long driverId) {
        return service.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 기사입니다."));
    }

    private void validateNotRegistered(Long userId) {
        if(service.findByUserId(userId).isPresent()) {
            throw new IllegalArgumentException("이미 기사로 등록된 유저입니다. userId=" + userId);
        }
    }
}
