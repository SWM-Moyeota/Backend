package team.codingforest.moyeota.driver.domain;

import io.micrometer.common.util.StringUtils;
import lombok.Getter;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.exception.DriverErrorCode;

import java.time.Instant;

/**
 *
 */
public class Driver {
    private final Long id;
    private final Long userId;
    private final String qualificationNumber;
    private Instant verifiedAt;
    private BankAccount bankAccount;
    private Vehicle vehicle;
    private DriverSetting setting;
    private DriverStatus status;
    private String fcmToken;
    private Instant deletedAt;

    private Driver(Long id, Long userId, String qualificationNumber, Instant verifiedAt,
                  BankAccount bankAccount, Vehicle vehicle, DriverSetting setting, DriverStatus status, String fcmToken, Instant deletedAt) {
        this.id = id;
        this.userId = userId;
        this.qualificationNumber = qualificationNumber;
        this.verifiedAt = verifiedAt;
        this.bankAccount = bankAccount;
        this.vehicle = vehicle;
        this.setting = setting;
        this.status = status;
        this.fcmToken = fcmToken;
        this.deletedAt = deletedAt;
    }

    /**
     *
     * @param userId
     * @param qualificationNumber
     * @param bankAccount
     * @return
     */
    public static Driver register(Long userId, String qualificationNumber, BankAccount bankAccount) {
        return new Driver(null, userId, qualificationNumber, null, bankAccount, null, DriverSetting.defaults(), DriverStatus.PENDING, null, null);
    }

    /**
     *  자격 검증 완료
     */
    public void verify(Instant verifiedAt) {
        if(status != DriverStatus.PENDING) {
            throw new BusinessException(DriverErrorCode.DRIVER_NOT_PENDING);
        }

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
        return !isWithdrawn() && status == DriverStatus.VERIFIED && vehicle != null && setting.isCallEnabled();
    }

    public void enableCall() {
        setting.enabledCall();
    }

    public void disableCall() {
        setting.disableCall();
    }

    /**
     *  Fcm 토큰 관련
     */
    public void registerFcmToken(String token) {
        if(StringUtils.isBlank(token)) {
            throw new BusinessException(DriverErrorCode.DRIVER_EMPTY_FCM_TOKEN);
        }

        this.fcmToken = token;
    }

    public boolean hasFcmToken() {
        return fcmToken != null;
    }

    public void clearFcmToken() {
        this.fcmToken = null;
    }

    public void clearBankAccount() {
        this.bankAccount = null;
    }

    public void withdraw(Instant withdrawnAt) {
        clearBankAccount();
        clearFcmToken();
        this.setting.disableCall();
        this.deletedAt = withdrawnAt;
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    /**
     *  영속 복원용
     */
    public static Driver load(Long id, Long userId, String qualificationNumber, Instant verifiedAt,
                                 BankAccount account, Vehicle vehicle, DriverSetting setting, DriverStatus status, String fcmToken, Instant deletedAt) {
        return new Driver(id, userId, qualificationNumber, verifiedAt, account, vehicle, setting, status, fcmToken, deletedAt);
    }

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getQualificationNumber() {
		return qualificationNumber;
	}

	public Instant getVerifiedAt() {
		return verifiedAt;
	}

	public BankAccount getBankAccount() {
		return bankAccount;
	}

	public Vehicle getVehicle() {
		return vehicle;
	}

	public DriverSetting getSetting() {
		return setting;
	}

	public DriverStatus getStatus() {
		return status;
	}

	public String getFcmToken() {
		return fcmToken;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}
}
