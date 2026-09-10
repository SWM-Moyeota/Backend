package team.codingforest.moyeota.driver.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import team.codingforest.moyeota.driver.domain.DriverSetting;

import java.time.Instant;

@Entity
@Getter
@Table(name = "driver_setting")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverSettingEntity {

    @Id
    private Long driverId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private DriverEntity driver;

    @Column(nullable = false)
    private boolean callEnabled;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

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

    public void update(DriverSetting setting) {
        this.callEnabled = setting.isCallEnabled();
    }
}
