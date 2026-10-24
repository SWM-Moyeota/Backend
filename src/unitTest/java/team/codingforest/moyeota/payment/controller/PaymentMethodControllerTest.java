package team.codingforest.moyeota.payment.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import team.codingforest.moyeota.common.exception.GlobalExceptionHandler;
import team.codingforest.moyeota.payment.service.PaymentMethodService;
import team.codingforest.moyeota.payment.entity.PaymentMethodType;
import team.codingforest.moyeota.payment.dto.PaymentMethodResponse;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.auth.presentation.security.CurrentUserArgumentResolver;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentMethodControllerTest {
    private PaymentMethodService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PaymentMethodService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentMethodController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedPrincipal(7L, UUID.randomUUID()), null, List.of()));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void 사용자와_카드정보를_클라이언트가_조작해도_빌링키와_인증사용자만_사용한다() throws Exception {
        when(service.register(7L, "bk-test")).thenReturn(new PaymentMethodResponse(
                100L, PaymentMethodType.CARD, "신한카드", "************1234", "VISA", Instant.now(), true));
        mvc.perform(post("/api/v1/payments/methods").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"billingKey":"bk-test","userId":999,"name":"위조 카드명"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.label").value("신한카드"))
                .andExpect(jsonPath("$.billingKey").doesNotExist());
        verify(service).register(7L, "bk-test");
    }

    @Test
    void 빈_키와_너무_긴_키는_서비스_호출_전에_거부한다() throws Exception {
        for(String key : List.of("", " ", "a".repeat(256))) {
            mvc.perform(post("/api/v1/payments/methods").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"billingKey\":\"" + key + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void 인증_사용자가_없으면_등록할_수_없다() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/v1/payments/methods").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"billingKey\":\"bk-test\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test
    void 카드_목록은_기본_여부를_포함하고_빌링키는_노출하지_않는다() throws Exception {
        when(service.list(7L)).thenReturn(List.of(new PaymentMethodResponse(
                100L, PaymentMethodType.CARD, "신한카드", "************1234", "VISA", Instant.now(), true)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/payments/methods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[0].billingKey").doesNotExist());
        verify(service).list(7L);
    }

    @Test
    void 기본_카드_선택에_인증된_사용자와_경로_ID를_전달한다() throws Exception {
        when(service.selectDefault(7L, 100L)).thenReturn(new PaymentMethodResponse(
                100L, PaymentMethodType.CARD, "신한카드", "************1234", "VISA", Instant.now(), true));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/payments/methods/100/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
        verify(service).selectDefault(7L, 100L);
    }

    @Test
    void 목록과_기본_선택도_인증이_필요하다() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/payments/methods"))
                .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/payments/methods/100/default"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void 카드_삭제에_인증된_사용자와_경로_ID를_전달한다() throws Exception {
        when(service.deleteCard(7L, 100L)).thenReturn(new PaymentMethodResponse(
                100L, PaymentMethodType.CARD, null, null, null, Instant.now(), false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/payments/methods/card/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.billingKey").doesNotExist());
        verify(service).deleteCard(7L, 100L);
    }

    @Test
    void 카드_삭제도_인증이_필요하다() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/payments/methods/card/100"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
