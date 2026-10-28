package team.codingforest.moyeota.driver.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.driver.domain.DriverSetting;

@Entity
@Getter
@Table(name = "driver_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverSettingEntity extends BaseTimeEntity {

    @Id
    private Long driverId;

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
        return DriverSetting.load(callEnabled);
    }

    public void update(DriverSetting setting) {
        this.callEnabled = setting.isCallEnabled();
    }
}
