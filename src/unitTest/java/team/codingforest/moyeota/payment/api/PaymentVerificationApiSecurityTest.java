package team.codingforest.moyeota.payment.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import team.codingforest.moyeota.payment.client.PortOneClient;
import team.codingforest.moyeota.common.exception.GlobalExceptionHandler;
import team.codingforest.moyeota.payment.controller.PaymentController;
import team.codingforest.moyeota.payment.dto.PaymentResultResponse;
import team.codingforest.moyeota.payment.dto.PaymentVerificationResponse;
import team.codingforest.moyeota.payment.entity.enums.PaymentStatus;
import team.codingforest.moyeota.payment.service.PaymentService;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.auth.application.AuthService;
import team.codingforest.moyeota.user.auth.presentation.security.JsonAuthenticationEntryPoint;
import team.codingforest.moyeota.user.auth.presentation.security.SecurityConfig;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 SecurityConfig와 JWT 필터를 통과시킨다. 토큰 검증 서비스 및 결제 호출은 모의 처리한다. */
@WebMvcTest(controllers = PaymentController.class)
@ActiveProfiles("dev")
@ContextConfiguration(classes = {PaymentController.class,
        SecurityConfig.class, JsonAuthenticationEntryPoint.class, GlobalExceptionHandler.class,
        PaymentVerificationApiSecurityTest.Configuration.class})
class PaymentVerificationApiSecurityTest {
    @TestConfiguration
    @EnableWebSecurity
    static class Configuration {}

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy filterChain;
    private MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean PaymentService payments;
    @MockitoBean PortOneClient portOne;

    @BeforeEach
    void 실제_보안필터_연결() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filterChain).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/payments/parties/10/verifications"
    })
    void 검증API는_인증없이_결제를_실행할_수_없다(String url) throws Exception {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"billingKey\":\"bk-test\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(payments, portOne);
    }

    @Test
    void 잘못된_토큰은_취소요청_전에_거부한다() throws Exception {
        when(auth.authenticate("invalid-token")).thenThrow(new UserException(UserErrorCode.UNAUTHORIZED));
        mvc.perform(post("/api/v1/payments/parties/10/verifications")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(payments, portOne);
    }

    @Test
    void 토큰의_사용자ID와_승인된_내부결제ID로_취소를_연결한다() throws Exception {
        when(auth.authenticate("valid-token")).thenReturn(new AuthenticatedPrincipal(7L, UUID.randomUUID()));
        when(payments.prePay(7L, 10L)).thenReturn(new PaymentResultResponse(42L, "pg-test", 7L, 12000, PaymentStatus.PAID));
        when(payments.cancelPrepayment(7L, 42L)).thenReturn(new PaymentVerificationResponse(42L, true));
        mvc.perform(post("/api/v1/payments/parties/10/verifications")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentId").value(42))
                .andExpect(jsonPath("$.verified").value(true));
        var order = inOrder(payments);
        order.verify(payments).prePay(7L, 10L);
        order.verify(payments).cancelPrepayment(7L, 42L);
    }
}
