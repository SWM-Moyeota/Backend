package team.codingforest.moyeota.driver.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class VehicleTest {

    @Test
    void 차량번호가_없으면_생성할_수_없다() {
        assertThatThrownBy(() -> new Vehicle(4, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Vehicle(4, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 좌석수가_1보다_작으면_생성할_수_없다() {
        assertThatThrownBy(() -> new Vehicle(0, "12가3456"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}