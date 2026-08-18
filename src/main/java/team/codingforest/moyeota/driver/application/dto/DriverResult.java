package team.codingforest.moyeota.driver.application.dto;

import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.enums.DriverStatus;

public record DriverResult(Long id, Long userId, DriverStatus status, boolean callEnabled) {

    public static DriverResult from(Driver driver) {
        return new DriverResult(driver.getId(), driver.getUserId(), driver.getStatus(), driver.getSetting().isCallEnabled());
    }
}
