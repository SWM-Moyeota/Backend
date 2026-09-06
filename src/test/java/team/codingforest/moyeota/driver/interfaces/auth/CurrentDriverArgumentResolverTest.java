package team.codingforest.moyeota.driver.interfaces.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.api.CurrentDriver;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.driver.domain.exception.DriverErrorCode;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 *  기사 API 인가의 유일한 방어선 - 토큰의 유저가 기사인지 확인해 driverId를 주입한다.
 *  URL에 남의 driverId를 적던 시절의 "기사 행세"가 불가능해졌는지가 보호 대상.
 */
class CurrentDriverArgumentResolverTest {

    private final InMemoryDrivers drivers = new InMemoryDrivers();
    private final CurrentDriverArgumentResolver resolver = new CurrentDriverArgumentResolver(drivers);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 기사로_등록된_유저면_그_기사의_driverId가_주입된다() throws Exception {
        Driver driver = drivers.save(Driver.register(7L, "서울-1234", new BankAccount("국민은행", "123-456")));
        로그인(7L);

        Object resolved = resolver.resolveArgument(param(), null, null, null);

        assertThat(resolved).isEqualTo(driver.getId());   // userId(7)가 아니라 driverId
    }

    @Test
    void 기사가_아닌_유저는_거부된다() {
        // 승객이 기사 API를 호출 - 로그인은 됐지만 기사 자격이 없다
        로그인(7L);

        assertThatThrownBy(() -> resolver.resolveArgument(param(), null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DriverErrorCode.DRIVER_NOT_REGISTERED);
    }

    @Test
    void 남의_기사_자격은_빌릴_수_없다() throws Exception {
        // 유저 7은 기사, 유저 8은 승객 - 8이 호출하면 7의 driverId가 나오면 안 된다
        drivers.save(Driver.register(7L, "서울-1234", new BankAccount("국민은행", "123-456")));
        로그인(8L);

        assertThatThrownBy(() -> resolver.resolveArgument(param(), null, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 인증_정보가_없으면_401이_난다() {
        assertThatThrownBy(() -> resolver.resolveArgument(param(), null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DriverErrorCode.UNAUTHORIZED);
    }

    @Test
    void CurrentDriver가_붙은_Long_파라미터만_지원한다() throws Exception {
        assertThat(resolver.supportsParameter(param())).isTrue();
    }

    private void 로그인(Long userId) {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, UUID.randomUUID());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private MethodParameter param() throws Exception {
        return new MethodParameter(Target.class.getDeclaredMethod("handle", Long.class), 0);
    }

    static class Target {
        void handle(@CurrentDriver Long driverId) {
        }
    }

    static class InMemoryDrivers implements Drivers {
        private final Map<Long, Driver> store = new HashMap<>();
        private Long sequence = 0L;

        @Override
        public Driver save(Driver driver) {
            Long id = driver.getId() != null ? driver.getId() : ++sequence;
            Driver saved = Driver.restore(id, driver.getUserId(), driver.getQualificationNumber(),
                    driver.getVerifiedAt(), driver.getBankAccount(), driver.getVehicle(),
                    driver.getSetting(), driver.getStatus(), driver.getFcmToken());
            store.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Driver> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Driver> findByUserId(Long userId) {
            return store.values().stream().filter(d -> d.getUserId().equals(userId)).findFirst();
        }
    }
}
