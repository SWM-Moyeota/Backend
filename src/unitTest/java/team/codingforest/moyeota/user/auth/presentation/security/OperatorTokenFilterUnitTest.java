package team.codingforest.moyeota.user.auth.presentation.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 *  운영자 토큰 필터만 따로 - 설정값이 비었을 때, 설정값 앞뒤 공백, 통과 시 붙는 권한
 */
class OperatorTokenFilterUnitTest {
    private static final String TOKEN = "operator-secret";
    private static final JsonAuthenticationEntryPoint ENTRY_POINT = new JsonAuthenticationEntryPoint(JsonMapper.builder().build());

    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @DisplayName("doFilter - 설정값이 비어 있으면(null·빈 값·공백) 헤더가 무엇이든 401 USER006 이고 다음 필터로 넘기지 않는다")
    @ParameterizedTest(name = "설정값 \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void blankConfiguredToken(String configured) throws Exception {

        /*
         * settings
         */
        OperatorTokenFilter filter = new OperatorTokenFilter(configured, ENTRY_POINT);
        MockHttpServletRequest request = request(configured == null ? "" : configured);
        MockHttpServletResponse response = new MockHttpServletResponse();

        /*
         * test
         */
        filter.doFilter(request, response, chain);

        /*
         * verify
         */
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"USER006\"");
        verify(chain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @DisplayName("doFilter - 맞으면 OPERATOR 권한으로 인증하고 다음 필터로 넘긴다")
    @Test
    void correctToken() throws Exception {

        /*
         * settings
         */
        OperatorTokenFilter filter = new OperatorTokenFilter(TOKEN, ENTRY_POINT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication[] seen = new Authentication[1];
        FilterChain capturing = (req, res) -> seen[0] = SecurityContextHolder.getContext().getAuthentication();

        /*
         * test
         */
        filter.doFilter(request(TOKEN), response, capturing);

        /*
         * verify
         */
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen[0]).isNotNull();
        assertThat(seen[0].isAuthenticated()).isTrue();
        assertThat(seen[0].getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_OPERATOR");
    }

    @DisplayName("doFilter - 설정값 앞뒤 공백(파라미터 스토어 값 끝 줄바꿈 등)은 떼고 비교한다")
    @Test
    void configuredTokenIsStripped() throws Exception {

        /*
         * settings
         */
        OperatorTokenFilter filter = new OperatorTokenFilter("  " + TOKEN + "\n", ENTRY_POINT);
        MockHttpServletResponse response = new MockHttpServletResponse();

        /*
         * test
         */
        filter.doFilter(request(TOKEN), response, chain);

        /*
         * verify
         */
        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @DisplayName("doFilter - 헤더가 없거나 틀리면 401 USER006 이고 인증 정보를 남기지 않는다")
    @Test
    void missingOrWrongToken() throws Exception {

        /*
         * settings
         */
        OperatorTokenFilter filter = new OperatorTokenFilter(TOKEN, ENTRY_POINT);
        MockHttpServletResponse missing = new MockHttpServletResponse();
        MockHttpServletResponse wrong = new MockHttpServletResponse();

        /*
         * test
         */
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/operator/no-show-objections/1/accept"), missing, chain);
        filter.doFilter(request(TOKEN.toUpperCase()), wrong, chain);

        /*
         * verify
         */
        assertThat(missing.getStatus()).isEqualTo(401);
        assertThat(wrong.getStatus()).isEqualTo(401);
        assertThat(wrong.getContentAsString()).contains("\"code\":\"USER006\"").doesNotContain(TOKEN);
        verify(chain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/operator/no-show-objections/1/accept");
        request.addHeader(OperatorTokenFilter.HEADER, token);
        return request;
    }
}
