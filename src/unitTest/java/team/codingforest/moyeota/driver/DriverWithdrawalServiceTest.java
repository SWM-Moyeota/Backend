package team.codingforest.moyeota.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DriverWithdrawalServiceTest {
    private static final Instant 탈퇴시각 = Instant.parse("2026-10-01T00:00:00Z");

    private DriverJpaTest drivers;
    private DriverWithdrawalService service;

    @BeforeEach
    void setUp() {
        drivers = new DriverJpaTest();
        service = new DriverWithdrawalService(drivers);
    }

    private Driver 기사등록(Long userId) {
        Driver driver = Driver.register(userId, "서울-1234-5678", new BankAccount("국민은행", "123-456-789012"));
        driver.registerFcmToken("token-" + userId);
        return drivers.save(driver);
    }

    @Test
    void 기사가_탈퇴하면_계좌와_토큰을_지우고_탈퇴_시각을_남긴다() {
        Driver saved = 기사등록(1L);

        service.withdraw(1L, 탈퇴시각);

        Driver withdrawn = drivers.findById(saved.getId()).orElseThrow();
        assertThat(withdrawn.getBankAccount()).isNull();
        assertThat(withdrawn.getFcmToken()).isNull();
        assertThat(withdrawn.getSetting().isCallEnabled()).isFalse();
        assertThat(withdrawn.getDeletedAt()).isEqualTo(탈퇴시각);
    }

    @Test
    void 다른_기사는_건드리지_않는다() {
        기사등록(1L);
        Driver other = 기사등록(2L);

        service.withdraw(1L, 탈퇴시각);

        assertThat(drivers.findById(other.getId()).orElseThrow().isWithdrawn()).isFalse();
    }

    @Test
    void 기사가_아닌_사용자면_아무것도_하지_않는다() {
        assertThatCode(() -> service.withdraw(999L, 탈퇴시각)).doesNotThrowAnyException();
    }

    @Test
    void 이미_탈퇴한_기사면_탈퇴_시각을_바꾸지_않는다() {
        Driver saved = 기사등록(1L);
        service.withdraw(1L, 탈퇴시각);

        service.withdraw(1L, 탈퇴시각.plus(Duration.ofDays(1)));

        assertThat(drivers.findById(saved.getId()).orElseThrow().getDeletedAt()).isEqualTo(탈퇴시각);
    }
}
