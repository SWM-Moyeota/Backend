package team.codingforest.moyeota.driver.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.driver.domain.Vehicle;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

    // ───────────────────────── FCM 토큰 일괄 조회 ─────────────────────────

    @Test
    void 토큰이_있는_기사만_한_번에_조회된다() {
        Driver 토큰기사 = registerAndSave();
        토큰기사.registerFcmToken("token-a");
        drivers.save(토큰기사);
        Driver 무토큰기사 = drivers.save(Driver.register(2L, "서울-9999-0000", new BankAccount("국민은행", "999-999-999999")));
        reload();

        Map<Long, String> tokens = drivers.findFcmTokens(List.of(토큰기사.getId(), 무토큰기사.getId(), 999L));

        assertThat(tokens).containsExactly(Map.entry(토큰기사.getId(), "token-a"));
    }

    @Test
    void 빈_토큰_문자열은_제외된다() {
        Driver driver = registerAndSave();
        reload();
        em.createQuery("update DriverEntity d set d.fcmToken = '' where d.id = :id").setParameter("id", driver.getId()).executeUpdate();
        reload();

        assertThat(drivers.findFcmTokens(List.of(driver.getId()))).isEmpty();
    }

    @Test
    void 빈_목록이면_조회하지_않고_빈_맵을_돌려준다() {
        assertThat(drivers.findFcmTokens(List.of())).isEmpty();
    }

    // ───────────────────────── 탈퇴 ─────────────────────────

    private static final Instant 탈퇴시각 = Instant.parse("2026-10-01T00:00:00Z");

    private Driver 차량있는기사(Long userId, String plateNumber) {
        Driver driver = Driver.register(userId, "서울-" + userId, new BankAccount("국민은행", "123-456-" + userId));
        driver.registerVehicle(new Vehicle(4, plateNumber, "중형"));
        driver.registerFcmToken("token-" + userId);
        return drivers.save(driver);
    }

    private Driver 탈퇴한기사(Long userId, String plateNumber, Instant withdrawnAt) {
        Driver driver = 차량있는기사(userId, plateNumber);
        driver.withdraw(withdrawnAt);
        return drivers.save(driver);
    }

    @Test
    void 탈퇴하면_계좌와_토큰은_비워지고_자격번호와_차량은_남는다() {
        Driver saved = 탈퇴한기사(1L, "12가3456", 탈퇴시각);
        reload();

        Driver reloaded = drivers.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getBankAccount()).isNull();
        assertThat(reloaded.getFcmToken()).isNull();
        assertThat(reloaded.getSetting().isCallEnabled()).isFalse();
        assertThat(reloaded.getDeletedAt()).isEqualTo(탈퇴시각);
        assertThat(reloaded.getUserId()).isEqualTo(1L);
        assertThat(reloaded.getQualificationNumber()).isEqualTo("서울-1");
        assertThat(reloaded.getVehicle().plateNumber()).isEqualTo("12가3456");
    }

    @Test
    void 보관_기간이_지난_탈퇴_기사만_차량과_설정까지_지워진다() {
        Instant now = Instant.now();
        Driver 지난기사 = 탈퇴한기사(1L, "11가1111", now.minus(Drivers.RETENTION).minus(Duration.ofHours(1)));
        Driver 기간안기사 = 탈퇴한기사(2L, "22나2222", now.minus(Drivers.RETENTION).plus(Duration.ofHours(1)));
        Driver 현역기사 = 차량있는기사(3L, "33다3333");
        reload();

        int deleted = drivers.deleteWithdrawnBefore(now.minus(Drivers.RETENTION));
        reload();

        assertThat(deleted).isEqualTo(1);
        assertThat(drivers.findById(지난기사.getId())).isEmpty();
        assertThat(drivers.findById(기간안기사.getId())).isPresent();
        assertThat(drivers.findById(현역기사.getId())).isPresent();
        assertThat(em.createQuery("select count(v) from VehicleEntity v", Long.class).getSingleResult()).isEqualTo(2L);
        assertThat(em.createQuery("select count(s) from DriverSettingEntity s", Long.class).getSingleResult()).isEqualTo(2L);
    }

    @Test
    void 지울_탈퇴_기사가_없으면_0을_돌려준다() {
        차량있는기사(1L, "12가3456");
        reload();

        assertThat(drivers.deleteWithdrawnBefore(Instant.now())).isZero();
        assertThat(repository.count()).isEqualTo(1L);
    }

    // ───────────────────────── 잠금 조회 ─────────────────────────

    @Test
    void 사용자_번호로_잠가_조회하면_행에_쓰기_잠금이_걸린다() {
        Driver saved = registerAndSave();
        reload();

        Driver locked = drivers.findByUserIdForUpdate(1L).orElseThrow();

        assertThat(locked.getId()).isEqualTo(saved.getId());
        assertThat(em.getLockMode(em.find(DriverEntity.class, saved.getId()))).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void 기사_번호로_잠가_조회하면_행에_쓰기_잠금이_걸린다() {
        Driver saved = registerAndSave();
        reload();

        Driver locked = drivers.findByIdForUpdate(saved.getId()).orElseThrow();

        assertThat(locked.getUserId()).isEqualTo(1L);
        assertThat(em.getLockMode(em.find(DriverEntity.class, saved.getId()))).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void 기사가_아니면_잠금_조회가_비어_있다() {
        registerAndSave();
        reload();

        assertThat(drivers.findByUserIdForUpdate(999L)).isEmpty();
        assertThat(drivers.findByIdForUpdate(999L)).isEmpty();
    }
}
