package team.codingforest.moyeota.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  예외가 클라이언트에게 실제로 어떤 HTTP 응답으로 배달되는지 검증한다.
 *  컨트롤러가 던진 예외 → 전역 핸들러 → {code, message} JSON 계약이 이 테스트의 보호 대상.
 *  특정 모듈의 컨트롤러에 기대지 않도록 테스트 안의 스텁 컨트롤러와 테스트 전용 에러 코드로 재현한다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @DisplayName("handleBusiness - 404 에러 코드면 404 와 code·message 로 내려간다")
    @Test
    void handleBusiness_notFound() throws Exception {

        /*
         * test
         */
        var result = mvc.perform(get("/test/business").param("code", "STUB_NOT_FOUND"));

        /*
         * verify
         */
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUB_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("없는 대상입니다."));
    }

    @DisplayName("handleBusiness - 409 에러 코드면 409 와 code 로 내려간다(앱은 code 를 보고 화면을 가른다)")
    @Test
    void handleBusiness_conflict() throws Exception {

        /*
         * test
         */
        var result = mvc.perform(get("/test/business").param("code", "STUB_CONFLICT"));

        /*
         * verify
         */
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUB_CONFLICT"))
                .andExpect(jsonPath("$.message").value("이미 처리된 요청입니다."));
    }

    @DisplayName("handleBusiness - 5xx 에러 코드도 같은 code·message 형식으로 내려간다")
    @Test
    void handleBusiness_serverError() throws Exception {

        /*
         * test
         */
        var result = mvc.perform(get("/test/business").param("code", "STUB_BAD_GATEWAY"));

        /*
         * verify
         */
        result.andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("STUB_BAD_GATEWAY"))
                .andExpect(jsonPath("$.message").value("외부 호출에 실패했습니다."));
    }

    @DisplayName("handleIllegalArgument - 도메인 가드의 IllegalArgumentException 은 400 INVALID_REQUEST 로 바뀐다")
    @Test
    void handleIllegalArgument() throws Exception {

        /*
         * test
         */
        // 커스텀 예외로 바꾸지 않은 다른 모듈의 가드가 500 으로 새지 않는 게 이 폴백의 존재 이유
        var result = mvc.perform(get("/test/illegal-argument"));

        /*
         * verify
         */
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("잘못된 인자입니다."));
    }

    @DisplayName("handleValidation - 본문 검증에 실패하면 400 과 어느 필드가 문제인지 내려준다")
    @Test
    void handleValidation() throws Exception {

        /*
         * test
         */
        var result = mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{}"));

        /*
         * verify
         */
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("name: 비어 있을 수 없습니다"));
    }

    @DisplayName("handleNotReadable - 본문이 없으면 400 과 공통 에러 형식으로 내려준다")
    @Test
    void handleNotReadable_noBody() throws Exception {

        /*
         * test
         */
        var result = mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON));

        /*
         * verify
         */
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 본문이 없거나 형식이 잘못되었습니다."));
    }

    @DisplayName("handleNotReadable - 깨진 JSON 이면 400 과 공통 에러 형식으로 내려준다")
    @Test
    void handleNotReadable_brokenJson() throws Exception {

        /*
         * test
         */
        var result = mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"));

        /*
         * verify
         */
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 본문이 없거나 형식이 잘못되었습니다."));
    }

    @DisplayName("handleTypeMismatch - 쿼리 파라미터 형식이 틀리면 400 과 어느 파라미터인지 내려준다")
    @Test
    void handleTypeMismatch() throws Exception {

        /*
         * test
         */
        var result = mvc.perform(get("/test/int-param").param("size", "abc"));

        /*
         * verify
         */
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("size: 형식이 올바르지 않습니다."));
    }

    /** 테스트 전용 에러 코드 - 상태별로 핸들러가 같은 JSON 계약을 지키는지 본다 */
    enum StubErrorCode implements ErrorCode {
        STUB_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 대상입니다."),
        STUB_CONFLICT(HttpStatus.CONFLICT, "이미 처리된 요청입니다."),
        STUB_BAD_GATEWAY(HttpStatus.BAD_GATEWAY, "외부 호출에 실패했습니다.");

        private final HttpStatus httpStatus;
        private final String message;

        StubErrorCode(HttpStatus httpStatus, String message) {
            this.httpStatus = httpStatus;
            this.message = message;
        }

        @Override
        public String getCode() {
            return name();
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public HttpStatus getHttpStatus() {
            return httpStatus;
        }
    }

    record StubRequest(@NotBlank(message = "비어 있을 수 없습니다") String name) {
    }

    /** 예외를 그대로 던지는 스텁 컨트롤러 */
    @RestController
    static class StubController {
        @GetMapping("/test/business")
        void business(@RequestParam StubErrorCode code) {
            throw new BusinessException(code);
        }

        @GetMapping("/test/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("잘못된 인자입니다.");
        }

        @PostMapping("/test/body")
        void body(@Valid @RequestBody StubRequest request) {
        }

        @GetMapping("/test/int-param")
        int intParam(@RequestParam int size) {
            return size;
        }
    }
}
