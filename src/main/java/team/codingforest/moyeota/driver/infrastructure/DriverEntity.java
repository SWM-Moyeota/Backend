package team.codingforest.moyeota.driver.infrastructure;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.DriverStatus;
import team.codingforest.moyeota.driver.domain.Vehicle;

import java.time.Instant;

@Table(name = "taxi_driver")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverEntity extends BaseTimeEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String qualificationNumber;

    @Column(nullable = false)
    private Instant verifiedAt;

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false)
    private String bankNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverStatus status;

    @OneToOne(mappedBy = "driver", cascade = CascadeType.ALL, orphanRemoval = true)
    private VehicleEntity vehicle;

    @OneToOne(mappedBy = "driver", cascade = CascadeType.ALL, orphanRemoval = true)
    private DriverSettingEntity setting;

    private DriverEntity(Long userId, String qualificationNumber, Instant verifiedAt, String bankName, String bankNumber, DriverStatus status) {
        this.userId = userId;
        this.qualificationNumber = qualificationNumber;
        this.verifiedAt = verifiedAt;
        this.bankName = bankName;
        this.bankNumber = bankNumber;
        this.status = status;
    }

    public static DriverEntity from(Driver driver) {
        DriverEntity entity = new DriverEntity(driver.getUserId(), driver.getQualificationNumber(), driver.getVerifiedAt(), driver.getBankAccount().bankName(), driver.getBankAccount().accountNumber(), driver.getStatus());

        if(driver.getVehicle() != null) {
            entity.vehicle = VehicleEntity.of(entity, driver.getVehicle());
        }
        entity.setting = DriverSettingEntity.of(entity, driver.getSetting());

        return entity;
    }

    public Driver toDomain() {
        return Driver.restore(getId(), userId, qualificationNumber, verifiedAt, new BankAccount(bankName, bankNumber), vehicle == null? null : vehicle.toDomain(), setting.toDomain(), status);
    }
}
