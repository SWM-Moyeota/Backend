package team.codingforest.moyeota.driver.infrastructure;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.DriverStatus;

import java.time.Instant;

@Table(name = "taxi_driver")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverEntity extends BaseTimeEntity {

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String qualificationNumber;

    private Instant verifiedAt;

    private String bankName;

    private String bankNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverStatus status;

    @OneToOne(mappedBy = "driver", cascade = CascadeType.ALL, orphanRemoval = true)
    private VehicleEntity vehicle;

    @OneToOne(mappedBy = "driver", cascade = CascadeType.ALL, orphanRemoval = true)
    private DriverSettingEntity setting;

    private String fcmToken;

    private Instant deletedAt;

    private DriverEntity(Long userId, String qualificationNumber, Instant verifiedAt, String bankName, String bankNumber, DriverStatus status) {
        this.userId = userId;
        this.qualificationNumber = qualificationNumber;
        this.verifiedAt = verifiedAt;
        this.bankName = bankName;
        this.bankNumber = bankNumber;
        this.status = status;
    }

    public static DriverEntity from(Driver driver) {
        BankAccount account = driver.getBankAccount();
        DriverEntity entity = new DriverEntity(driver.getUserId(), driver.getQualificationNumber(), driver.getVerifiedAt(),
                account == null ? null : account.bankName(), account == null ? null : account.accountNumber(), driver.getStatus());

        if(driver.getVehicle() != null) {
            entity.vehicle = VehicleEntity.of(entity, driver.getVehicle());
        }
        entity.setting = DriverSettingEntity.of(entity, driver.getSetting());
        entity.fcmToken = driver.getFcmToken();
        entity.deletedAt = driver.getDeletedAt();

        return entity;
    }

    public Driver toDomain() {
        BankAccount account = bankName == null ? null : new BankAccount(bankName, bankNumber);
        return Driver.restore(getId(), userId, qualificationNumber, verifiedAt, account, vehicle == null? null : vehicle.toDomain(), setting.toDomain(), status, fcmToken, deletedAt);
    }

    public void update(Driver driver) {
        this.verifiedAt = driver.getVerifiedAt();
        this.status = driver.getStatus();
        BankAccount account = driver.getBankAccount();
        this.bankName = account == null ? null : account.bankName();
        this.bankNumber = account == null ? null : account.accountNumber();
        this.fcmToken = driver.getFcmToken();
        this.deletedAt = driver.getDeletedAt();

        if(driver.getVehicle() != null) {
            if(this.vehicle == null) {
                this.vehicle = VehicleEntity.of(this, driver.getVehicle());
            }
            else {
                this.vehicle.update(driver.getVehicle());
            }
        }
        this.setting.update(driver.getSetting());
    }
}
