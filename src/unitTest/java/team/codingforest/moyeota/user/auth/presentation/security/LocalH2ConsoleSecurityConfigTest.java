package team.codingforest.moyeota.user.auth.presentation.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class LocalH2ConsoleSecurityConfigTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(LocalH2ConsoleSecurityConfig.class, FallbackSecurity.class);

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class FallbackSecurity {
        @Bean
        SecurityFilterChain fallback(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().denyAll()).build();
        }
    }

    @Test
    void 로컬_콘솔만_접근과_프레임을_허용한다() {
        runner.withPropertyValues("spring.profiles.active=test-member").run(context -> {
            var proxy = context.getBean(FilterChainProxy.class);
            for (String address : new String[]{"127.0.0.1", "::1"}) {
                var response = request(proxy, "/h2-console/login.do", address, "POST");
                assertThat(response.getStatus()).isEqualTo(202);
                assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
            }
            assertThat(request(proxy, "/h2-console/", "192.0.2.1", "GET").getStatus()).isEqualTo(403);
            assertThat(request(proxy, "/api/v1/payments/methods", "127.0.0.1", "GET").getStatus()).isEqualTo(403);
            assertThat(request(proxy, "/h2-console-other", "127.0.0.1", "GET").getStatus()).isEqualTo(403);
        });
    }

    @Test
    void 기본과_운영_프로필에는_콘솔_허용_체인이_없다() {
        for (String profiles : new String[]{"default", "dev", "prod", "test-member,dev", "test-member,prod"}) {
            runner.withPropertyValues("spring.profiles.active=" + profiles).run(context ->
                    assertThat(context).doesNotHaveBean("h2ConsoleSecurityFilterChain"));
        }
    }

    private MockHttpServletResponse request(FilterChainProxy proxy, String path, String address, String method) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(address);
        var response = new MockHttpServletResponse();
        proxy.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(202));
        return response;
    }
}
