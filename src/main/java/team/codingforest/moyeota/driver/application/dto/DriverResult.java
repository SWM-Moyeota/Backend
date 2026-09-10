package team.codingforest.moyeota.driver.application.dto;

import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.enums.DriverStatus;

/** name 은 user 모듈의 닉네임 - 소셜 가입 직후처럼 미설정이면 null */
public record DriverResult(Long id, Long userId, String name, DriverStatus status, boolean callEnabled) {

    public static DriverResult from(Driver driver, String name) {
        return new DriverResult(driver.getId(), driver.getUserId(), name, driver.getStatus(), driver.getSetting().isCallEnabled());
    }
}
