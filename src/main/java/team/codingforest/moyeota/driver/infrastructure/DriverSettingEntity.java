package team.codingforest.moyeota.driver.infrastructure;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.driver.domain.DriverSetting;

import java.time.Instant;

@Entity
@Getter
@Table(name = "driver_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverSettingEntity extends BaseTimeEntity {

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private DriverEntity driver;

    @Column(nullable = false)
    private boolean callEnabled;

    private DriverSettingEntity(DriverEntity driver, boolean callEnabled) {
        this.callEnabled = callEnabled;
        this.driver = driver;
    }

    public static DriverSettingEntity of(DriverEntity driver, DriverSetting setting) {
        return new DriverSettingEntity(driver, setting.isCallEnabled());
    }

    public DriverSetting toDomain() {
        return DriverSetting.restore(callEnabled);
    }
}
