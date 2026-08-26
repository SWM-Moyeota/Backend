package team.codingforest.moyeota.driver.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.driver.domain.enums.DriverStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class DriverTest {
    private static final Long 유저 = 1L;
    private static final String 자격번호 = "서울-1234-5678";
    private static final BankAccount 계좌 = new BankAccount("국민은행", "123-456-789012");
    private static final Vehicle 소나타 = new Vehicle(4, "12가3456");
    private static final Instant 지금 = Instant.now();

    private Driver 등록된기사() {
        return Driver.register(유저, 자격번호, 계좌);
    }

    private Driver 운행가능기사() {
        Driver driver = 등록된기사();
        driver.verify(지금);
        driver.registerVehicle(소나타);
        return driver;
    }

    @Test
    void 등록하면_PENDING_상태로_시작한다() {
        Driver driver = 등록된기사();

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.PENDING);
        assertThat(driver.getVerifiedAt()).isNull();
    }

    @Test
    void 검증하면_VERIFIED가_되고_검증일시가_기록된다() {
        Driver driver = 등록된기사();

        driver.verify(지금);

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.VERIFIED);
        assertThat(driver.getVerifiedAt()).isEqualTo(지금);
    }

    @Test
    void 이미_검증된_기사는_다시_검증할_수_없다() {
        Driver driver = 등록된기사();
        driver.verify(지금);

        assertThatThrownBy(() -> driver.verify(지금))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 검증되고_차량이_있으면_콜을_받을_수_있다() {
        Driver driver = 운행가능기사();

        assertThat(driver.canReceiveCalls()).isTrue();
    }

    @Test
    void 검증_전에는_콜을_받을_수_없다() {
        Driver driver = 등록된기사();
        driver.registerVehicle(소나타);

        assertThat(driver.canReceiveCalls()).isFalse();
    }

    @Test
    void 차량이_없으면_콜을_받을_수_없다() {
        Driver driver = 등록된기사();
        driver.verify(지금);

        assertThat(driver.canReceiveCalls()).isFalse();
    }

    @Test
    void 콜을_끄면_콜을_받을_수_없다() {
        Driver driver = 운행가능기사();

        driver.disableCall();

        assertThat(driver.canReceiveCalls()).isFalse();
    }

    @Test
    void 콜을_다시_켜면_콜을_받을_수_있다() {
        Driver driver = 운행가능기사();
        driver.disableCall();

        driver.enableCall();

        assertThat(driver.canReceiveCalls()).isTrue();
    }

    // ───────────────────────── FCM 토큰 ─────────────────────────

    @Test
    void 토큰을_등록하면_보유상태가_된다() {
        Driver driver = 등록된기사();

        driver.registerFcmToken("token-abc");

        assertThat(driver.hasFcmToken()).isTrue();
        assertThat(driver.getFcmToken()).isEqualTo("token-abc");
    }

    @Test
    void null_토큰은_등록할_수_없다() {
        Driver driver = 등록된기사();

        assertThatThrownBy(() -> driver.registerFcmToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 공백_토큰은_등록할_수_없다() {
        Driver driver = 등록된기사();

        assertThatThrownBy(() -> driver.registerFcmToken(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> driver.registerFcmToken("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 토큰을_다시_등록하면_덮어쓴다() {
        Driver driver = 등록된기사();
        driver.registerFcmToken("old-token");

        driver.registerFcmToken("new-token");

        assertThat(driver.getFcmToken()).isEqualTo("new-token");   // 옛 토큰이 남으면 죽은 기기로 발송된다
    }

    @Test
    void 토큰을_제거하면_보유상태가_아니다() {
        Driver driver = 등록된기사();
        driver.registerFcmToken("token-abc");

        driver.clearFcmToken();

        assertThat(driver.hasFcmToken()).isFalse();
        assertThat(driver.getFcmToken()).isNull();
    }

    @Test
    void 토큰이_없어도_제거는_예외없이_동작한다() {
        Driver driver = 등록된기사();

        driver.clearFcmToken();   // 로그아웃 API 중복 호출 - 멱등해야 함

        assertThat(driver.hasFcmToken()).isFalse();
    }

    @Test
    void 검증_전_기사도_토큰은_등록할_수_있다() {
        Driver driver = 등록된기사();   // PENDING - 앱 로그인은 자격검증과 무관

        driver.registerFcmToken("token-abc");

        assertThat(driver.hasFcmToken()).isTrue();
    }
}