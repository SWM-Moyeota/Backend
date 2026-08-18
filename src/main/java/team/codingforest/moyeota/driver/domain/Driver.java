package team.codingforest.moyeota.driver.domain;

import lombok.Getter;
import team.codingforest.moyeota.driver.domain.enums.DriverStatus;

import java.time.Instant;

@Getter
public class Driver {
    private final Long id;
    private final Long userId;
    private final String qualificationNumber;
    private Instant verifiedAt;
    private BankAccount bankAccount;
    private Vehicle vehicle;
    private DriverSetting setting;
    private DriverStatus status;

    private Driver(Long id, Long userId, String qualificationNumber, Instant verifiedAt,
                  BankAccount bankAccount, Vehicle vehicle, DriverSetting setting, DriverStatus status) {
        this.id = id;
        this.userId = userId;
        this.qualificationNumber = qualificationNumber;
        this.verifiedAt = verifiedAt;
        this.bankAccount = bankAccount;
        this.vehicle = vehicle;
        this.setting = setting;
        this.status = status;
    }

    /**
     *  기사 등록 신청 PENDING으로 시작
     */
    public static Driver register(Long userId, String qualificationNumber, BankAccount bankAccount) {
        return new Driver(null, userId, qualificationNumber, null, bankAccount, null, DriverSetting.defaults(), DriverStatus.PENDING);
    }

    /**
     *  자격 검증 완료
     */
    public void verify(Instant verifiedAt) {
        if(status != DriverStatus.PENDING) throw new IllegalArgumentException("검증 대기 상태가 아닙니다.");

        this.verifiedAt = verifiedAt;
        this.status = DriverStatus.VERIFIED;
    }

    /**
     *  차량 등록 완료
     */
    public void registerVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    /**
     *  콜 받을 수 있는지 여부확인
     */
    public boolean canReceiveCalls() {
        return status == DriverStatus.VERIFIED && vehicle != null && setting.isCallEnabled();
    }

    public void enableCall() {
        setting.enabledCall();
    }

    public void disableCall() {
        setting.disableCall();
    }

    /**
     *  영속 복원용
     */
    public static Driver restore(Long id, Long userId, String qualificationNumber, Instant verifiedAt,
                                 BankAccount account, Vehicle vehicle, DriverSetting setting, DriverStatus status) {
        return new Driver(id, userId, qualificationNumber, verifiedAt, account, vehicle, setting, status);
    }
}
