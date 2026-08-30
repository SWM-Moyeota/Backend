package team.codingforest.moyeota.driver.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.driver.domain.BankAccount;
import team.codingforest.moyeota.driver.domain.Driver;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DriverAccessServiceTest {

    private DriverJpaTest drivers;
    private DriverAccessService service;

    @BeforeEach
    void setUp() {
        drivers = new DriverJpaTest();
        service = new DriverAccessService(drivers);
    }

    private Long 기사등록(Long userId, String fcmToken) {
        Driver driver = Driver.register(userId, "서울-1234-5678", new BankAccount("국민은행", "123-456-789012"));
        Driver saved = drivers.save(driver);

        if(fcmToken != null) {
            saved.registerFcmToken(fcmToken);
            saved = drivers.save(saved);
        }

        return saved.getId();
    }

    @Test
    void 토큰이_있는_기사만_맵에_담긴다() {
        Long 토큰기사 = 기사등록(1L, "token-1");
        Long 무토큰기사 = 기사등록(2L, null);

        Map<Long, String> tokens = service.findFcmTokens(List.of(토큰기사, 무토큰기사));

        assertThat(tokens).containsOnlyKeys(토큰기사);
        assertThat(tokens.get(토큰기사)).isEqualTo("token-1");
    }

    @Test
    void 없는_기사ID가_섞여있어도_예외없이_건너뛴다() {
        Long 토큰기사 = 기사등록(1L, "token-1");

        // 콜 후보 등록 후 기사가 탈퇴/삭제된 시나리오 - 여기서 터지면 콜 전체가 실패함
        Map<Long, String> tokens = service.findFcmTokens(List.of(토큰기사, 999L));

        assertThat(tokens).containsOnlyKeys(토큰기사);
    }

    @Test
    void 아무도_토큰이_없으면_빈_맵을_반환한다() {
        Long 기사1 = 기사등록(1L, null);
        Long 기사2 = 기사등록(2L, null);

        assertThat(service.findFcmTokens(List.of(기사1, 기사2))).isEmpty();
    }

    @Test
    void 빈_후보_리스트면_빈_맵을_반환한다() {
        assertThat(service.findFcmTokens(List.of())).isEmpty();
    }

    @Test
    void 중복_ID가_있어도_한_번만_담긴다() {
        Long 토큰기사 = 기사등록(1L, "token-1");

        Map<Long, String> tokens = service.findFcmTokens(List.of(토큰기사, 토큰기사));

        assertThat(tokens).hasSize(1);   // 중복이면 같은 기사에게 알림이 두 번 갈 수 있다
    }
}
