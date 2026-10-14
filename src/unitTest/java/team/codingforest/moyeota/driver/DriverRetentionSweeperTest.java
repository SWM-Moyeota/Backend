package team.codingforest.moyeota.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DriverRetentionSweeperTest {
    private DriverJpaTest drivers;
    private DriverRetentionSweeper sweeper;

    @BeforeEach
    void setUp() {
        drivers = new DriverJpaTest();
        sweeper = new DriverRetentionSweeper(drivers);
    }

    private Driver 기사(Long userId, Instant withdrawnAt) {
        Driver driver = drivers.save(Driver.register(userId, "서울-1234-5678", new BankAccount("국민은행", "123-456-789012")));
        if(withdrawnAt != null) {
            driver.withdraw(withdrawnAt);
            driver = drivers.save(driver);
        }
        return driver;
    }

    @Test
    void 보관_기간이_지난_탈퇴_기사만_지운다() {
        Instant now = Instant.now();
        Driver 지난기사 = 기사(1L, now.minus(Drivers.RETENTION).minus(Duration.ofHours(1)));
        Driver 기간안기사 = 기사(2L, now.minus(Drivers.RETENTION).plus(Duration.ofHours(1)));
        Driver 현역기사 = 기사(3L, null);

        sweeper.sweep();

        assertThat(drivers.findById(지난기사.getId())).isEmpty();
        assertThat(drivers.findById(기간안기사.getId())).isPresent();
        assertThat(drivers.findById(현역기사.getId())).isPresent();
    }

    @Test
    void 보관_기간은_180일이다() {
        assertThat(Drivers.RETENTION).isEqualTo(Duration.ofDays(180));
    }
}
