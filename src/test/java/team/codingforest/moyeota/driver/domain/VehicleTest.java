package team.codingforest.moyeota.driver.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.domain.exception.DriverErrorCode;

import static org.assertj.core.api.Assertions.*;

class VehicleTest {

    @Test
    void 차량번호가_없으면_생성할_수_없다() {
        assertThatThrownBy(() -> new Vehicle(4, null, "중형"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DriverErrorCode.INVALID_PLATE_NUMBER);

        assertThatThrownBy(() -> new Vehicle(4, "  ", "중형"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DriverErrorCode.INVALID_PLATE_NUMBER);
    }

    @Test
    void 좌석수가_1보다_작으면_생성할_수_없다() {
        assertThatThrownBy(() -> new Vehicle(0, "12가3456", "중형"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DriverErrorCode.INVALID_VEHICLE_SEATS);
    }
}
