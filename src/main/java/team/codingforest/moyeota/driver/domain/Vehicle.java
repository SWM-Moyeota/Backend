package team.codingforest.moyeota.driver.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.domain.exception.DriverErrorCode;

public record Vehicle(Integer seats, String plateNumber, String type) {
    public Vehicle {
        if(plateNumber == null || plateNumber.isBlank()) throw new BusinessException(DriverErrorCode.INVALID_PLATE_NUMBER);

        if(seats == null || seats < 1) throw new BusinessException(DriverErrorCode.INVALID_VEHICLE_SEATS);
    }
}
