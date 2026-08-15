package team.codingforest.moyeota.driver.infrastructure;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.driver.domain.Vehicle;

@Entity
@Getter
@Table(name = "taxi")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleEntity extends BaseTimeEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxi_driver_id", nullable = false)
    private DriverEntity driver;

    @Column(nullable = false)
    private Integer seats;

    @Column(nullable = false)
    private String plateNumber;

    private VehicleEntity(DriverEntity driver, Integer seats, String plateNumber) {
        this.driver = driver;
        this.seats = seats;
        this.plateNumber = plateNumber;
    }

    public static VehicleEntity of(DriverEntity driver, Vehicle vehicle) {
        return new VehicleEntity(driver, vehicle.seats(), vehicle.plateNumber());
    }

    public Vehicle toDomain() {
        return new Vehicle(seats, plateNumber);
    }

    public void update(Vehicle vehicle) {
        this.seats = vehicle.seats();
        this.plateNumber = vehicle.plateNumber();
    }
}
