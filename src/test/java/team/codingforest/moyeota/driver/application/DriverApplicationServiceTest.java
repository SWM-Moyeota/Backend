package team.codingforest.moyeota.driver.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.driver.application.dto.DriverResult;
import team.codingforest.moyeota.driver.application.dto.RegisterDriverCommand;
import team.codingforest.moyeota.driver.domain.enums.DriverStatus;

import static org.assertj.core.api.Assertions.*;

class DriverApplicationServiceTest {
    private static final Long 유저 = 1L;
    private static final Long 다른유저 = 2L;

    private DriverApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DriverApplicationService(new DriverJpaTest());
    }

    private RegisterDriverCommand 등록명령(Long userId) {
        return new RegisterDriverCommand(userId, "서울-1234-5678", "국민은행", "123-456-789012");
    }

    @Test
    void 기사를_등록하면_PENDING_상태로_저장된다() {
        DriverResult result = service.register(등록명령(유저));

        assertThat(result.id()).isNotNull();
        assertThat(result.status()).isEqualTo(DriverStatus.PENDING);
        assertThat(result.callEnabled()).isTrue();
    }

    @Test
    void 이미_등록된_유저는_다시_등록할_수_없다() {
        service.register(등록명령(유저));

        assertThatThrownBy(() -> service.register(등록명령(유저)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 다른_유저는_각자_등록할_수_있다() {
        service.register(등록명령(유저));

        DriverResult result = service.register(등록명령(다른유저));

        assertThat(result.userId()).isEqualTo(다른유저);
    }

    @Test
    void 자격을_검증하면_VERIFIED가_된다() {
        DriverResult registered = service.register(등록명령(유저));

        service.verify(registered.id());

        assertThat(service.getByUserId(유저).status()).isEqualTo(DriverStatus.VERIFIED);
    }

    @Test
    void 존재하지_않는_기사를_검증하면_예외가_발생한다() {
        assertThatThrownBy(() -> service.verify(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 콜을_끄면_수신_상태에_반영된다() {
        DriverResult registered = service.register(등록명령(유저));

        service.disableCall(registered.id());

        assertThat(service.getByUserId(유저).callEnabled()).isFalse();
    }

    @Test
    void 기사로_등록되지_않은_유저를_조회하면_예외가_발생한다() {
        assertThatThrownBy(() -> service.getByUserId(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}