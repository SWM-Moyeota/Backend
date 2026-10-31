package team.codingforest.moyeota.common;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.Drivers;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.auth.application.AuthService;
import team.codingforest.moyeota.user.auth.presentation.security.JsonAuthenticationEntryPoint;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 *  @WebMvcTest 공통 설정. SecurityConfig·DriverWebConfig 가 WebMvcConfigurer 라 슬라이스에 같이 뜨므로
 *  그 의존성(AuthService, Drivers, EntryPoint)을 여기서 끊는다. 시큐리티 필터 체인은 실제 그대로 탄다.
 */
@Import(JsonAuthenticationEntryPoint.class)
public abstract class ControllerUnitTestSupport {
    protected static final Long USER_ID = 1L;
    protected static final UUID PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    protected static final Long DRIVER_ID = 9L;
    private static final String TOKEN = "test-access-token";

    @Autowired
    protected MockMvc mockMvc;
    @MockitoBean
    protected AuthService authService;
    @MockitoBean
    protected Drivers drivers;

    @BeforeEach
    void stubAuthentication() {
        doReturn(new AuthenticatedPrincipal(USER_ID, PUBLIC_ID)).when(authService).authenticate(TOKEN);
    }

    /** @CurrentUser 에 USER_ID / PUBLIC_ID 가 주입된다 */
    protected static RequestPostProcessor loginUser() {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
            return request;
        };
    }

    /** @CurrentDriver 에 DRIVER_ID 가 주입된다 */
    protected RequestPostProcessor loginDriver() {
        Driver driver = mock(Driver.class);
        doReturn(DRIVER_ID).when(driver).getId();
        doReturn(false).when(driver).isWithdrawn();
        doReturn(Optional.of(driver)).when(drivers).findByUserId(USER_ID);

        return loginUser();
    }
}
