package team.codingforest.moyeota.driver.infrastructure;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Vehicle;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({DriverJpa.class, JpaAuditingConfig.class})
class DriverJpaPersistenceTest {
    private final DriverJpa drivers;
    private final DriverJpaRepository repository;
    private final EntityManager em;

    @Autowired
    DriverJpaPersistenceTest(DriverJpa drivers, DriverJpaRepository repository, EntityManager em) {
        this.drivers = drivers;
        this.repository = repository;
        this.em = em;
    }

    /** 1차 캐시를 비워 실제 DB 행 기준으로 다시 읽게 한다 */
    private void reload() {
        em.flush();
        em.clear();
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

    // ───────────────────────── 차량 ─────────────────────────

    @Test
    void 차량과_함께_등록하면_taxi_행이_저장된다() {
        // 기사 등록 API 가 차량을 같이 받으므로 cascade 로 taxi 행까지 남아야 콜을 받을 수 있다
        Driver driver = Driver.register(1L, "서울-1234-5678", new BankAccount("국민은행", "123-456-789012"));
        driver.registerVehicle(new Vehicle(4, "12가3456", "중형"));
        Driver saved = drivers.save(driver);
        reload();

        Vehicle vehicle = drivers.findById(saved.getId()).orElseThrow().getVehicle();

        assertThat(vehicle).isNotNull();
        assertThat(vehicle.plateNumber()).isEqualTo("12가3456");
        assertThat(vehicle.seats()).isEqualTo(4);
        assertThat(vehicle.type()).isEqualTo("중형");
        assertThat(em.createQuery("select count(v) from VehicleEntity v", Long.class).getSingleResult()).isEqualTo(1L);
    }

    @Test
    void 차량이_없던_기사에게_나중에_등록해도_저장된다() {
        Driver saved = registerAndSave();
        reload();

        Driver reloaded = drivers.findById(saved.getId()).orElseThrow();
        reloaded.registerVehicle(new Vehicle(4, "12가3456", "중형"));
        drivers.save(reloaded);
        reload();

        assertThat(drivers.findById(saved.getId()).orElseThrow().getVehicle()).isNotNull();
    }

    @Test
    void 차량을_교체하면_행이_늘지_않고_차종까지_갱신된다() {
        Driver driver = Driver.register(1L, "서울-1234-5678", new BankAccount("국민은행", "123-456-789012"));
        driver.registerVehicle(new Vehicle(4, "12가3456", "중형"));
        Driver saved = drivers.save(driver);
        reload();

        Driver reloaded = drivers.findById(saved.getId()).orElseThrow();
        reloaded.registerVehicle(new Vehicle(6, "78나9012", "대형"));
        drivers.save(reloaded);
        reload();

        Vehicle vehicle = drivers.findById(saved.getId()).orElseThrow().getVehicle();
        assertThat(vehicle.plateNumber()).isEqualTo("78나9012");
        assertThat(vehicle.seats()).isEqualTo(6);
        assertThat(vehicle.type()).isEqualTo("대형");
        assertThat(em.createQuery("select count(v) from VehicleEntity v", Long.class).getSingleResult()).isEqualTo(1L);
    }
}
