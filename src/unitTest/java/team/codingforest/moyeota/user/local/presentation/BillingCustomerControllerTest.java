package team.codingforest.moyeota.user.local.presentation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import team.codingforest.moyeota.common.exception.GlobalExceptionHandler;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.auth.presentation.security.CurrentUserArgumentResolver;
import team.codingforest.moyeota.user.local.application.LocalUserService;
import team.codingforest.moyeota.user.local.dto.BillingCustomerResponse;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BillingCustomerControllerTest {
    private LocalUserService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(LocalUserService.class);
        mvc = MockMvcBuilders.standaloneSetup(new LocalUserController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void 요청의_사용자ID를_무시하고_인증된_본인정보만_조회한다() throws Exception {
        UUID uuid = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedPrincipal(7L, uuid), null, List.of()));
        when(service.getBillingCustomer(7L)).thenReturn(new BillingCustomerResponse(
                uuid, "닉네임", "실명", "01012345678", "member@example.com"));
        mvc.perform(get("/api/v1/local/users/billing-customer").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.fullName").value("실명"));
        verify(service).getBillingCustomer(7L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void 인증이_없으면_개인정보를_조회하지_않는다() throws Exception {
        mvc.perform(get("/api/v1/local/users/billing-customer"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
