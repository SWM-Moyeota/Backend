package team.codingforest.moyeota.driver.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({DriverJpa.class, JpaAuditingConfig.class})
class DriverJpaPersistenceTest {
    private final DriverJpa drivers;
    private final DriverJpaRepository repository;

    @Autowired
    DriverJpaPersistenceTest(DriverJpa drivers, DriverJpaRepository repository) {
        this.drivers = drivers;
        this.repository = repository;
    }

    private Driver registerAndSave() {
        Driver driver = Driver.register(1L, "서울-1234-5678", new BankAccount("국민은행", "123-456-789012"));
        return drivers.save(driver);
    }

    @Test
    void 기존_기사를_다시_저장해도_행이_늘어나지_않는다() {
        Driver saved = registerAndSave();
        long before = repository.count();

        saved.registerFcmToken("token-abc");
        drivers.save(saved);

        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void 토큰을_등록하면_같은_id로_조회했을_때_유지된다() {
        Driver saved = registerAndSave();

        saved.registerFcmToken("token-abc");
        drivers.save(saved);

        // 콜을 뿌릴 때 findFcmTokens가 이 id로 조회함 - 여기서 null이면 알림이 못 감
        assertThat(drivers.findById(saved.getId()).orElseThrow().getFcmToken()).isEqualTo("token-abc");
    }

    @Test
    void 토큰을_제거하면_같은_id로_조회했을_때_사라진다() {
        Driver saved = registerAndSave();
        saved.registerFcmToken("token-abc");
        drivers.save(saved);

        Driver reloaded = drivers.findById(saved.getId()).orElseThrow();
        reloaded.clearFcmToken();
        drivers.save(reloaded);

        assertThat(drivers.findById(saved.getId()).orElseThrow().getFcmToken()).isNull();
    }
}
