package team.codingforest.moyeota.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.driver.application.DriverApplicationService;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.driver.interfaces.DriverController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 *  예외가 클라이언트에게 실제로 어떤 HTTP 응답으로 배달되는지 검증한다.
 *  서비스가 던진 예외 → 전역 핸들러 → {code, message} JSON 계약이 이 테스트의 보호 대상.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        DriverApplicationService service = new DriverApplicationService(new InMemoryDrivers());

        mvc = MockMvcBuilders.standaloneSetup(new DriverController(service), new IllegalArgumentThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 없는_기사_조회는_404와_에러코드로_내려간다() throws Exception {
        mvc.perform(get("/api/v1/drivers/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"))
                .andExpect(jsonPath("$.message").value("기사로 등록되지 않은 유저입니다."));
    }

    @Test
    void 중복_기사_등록은_409와_에러코드로_내려간다() throws Exception {
        String body = """
                {"userId": 1, "qualificationNumber": "서울-1234", "bankName": "국민은행", "accountNumber": "123-456"}
                """;
        mvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        // 같은 유저 재등록 - 앱은 이 코드를 보고 "이미 기사입니다" 화면으로 분기한다
        mvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_ALREADY_REGISTERED"));
    }

    @Test
    void 도메인_가드의_IllegalArgumentException은_400으로_변환된다() throws Exception {
        // 커스텀 예외로 교체하지 않은 다른 모듈의 가드가 500으로 새지 않는 게 이 폴백의 존재 이유
        mvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("잘못된 인자입니다."));
    }

    @Test
    void 본문_검증_실패는_400과_어느_필드가_문제인지_내려준다() throws Exception {
        mvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 검증_전_기사를_다시_검증하면_409가_내려간다() throws Exception {
        String body = """
                {"userId": 1, "qualificationNumber": "서울-1234", "bankName": "국민은행", "accountNumber": "123-456"}
                """;
        mvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON).content(body));
        mvc.perform(post("/api/v1/drivers/1/verify")).andExpect(status().isNoContent());

        // 검증 API 이중 탭 - 도메인(Driver.verify)이 던진 예외도 같은 JSON 계약으로 나가야 한다
        mvc.perform(post("/api/v1/drivers/1/verify"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_PENDING"));
    }

    /** IllegalArgumentException 폴백 검증용 - driver 모듈엔 IAE가 남아있지 않아 스텁으로 재현 */
    @RestController
    static class IllegalArgumentThrowingController {
        @GetMapping("/test/illegal-argument")
        void boom() {
            throw new IllegalArgumentException("잘못된 인자입니다.");
        }
    }

    /** 인메모리 기사 저장소 - save가 id를 부여하고 restore로 복사본을 보관 */
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
            return store.values().stream()
                    .filter(driver -> driver.getUserId().equals(userId))
                    .findFirst();
        }
    }
}
