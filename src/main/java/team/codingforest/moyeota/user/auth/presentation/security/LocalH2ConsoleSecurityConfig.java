package team.codingforest.moyeota.user.auth.presentation.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

/** 테스트 회원 프로필의 로컬 콘솔에만 적용한다. API는 기존 JWT 체인이 처리한다. */
@Configuration(proxyBeanMethods = false)
@Profile("test-member & !dev & !prod")
public class LocalH2ConsoleSecurityConfig {
    @Bean
    @Order(0)
    SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(request -> {
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    return path.equals("/h2-console") || path.startsWith("/h2-console/");
                })
                .authorizeHttpRequests(auth -> auth.anyRequest().access(
                        new WebExpressionAuthorizationManager("hasIpAddress('127.0.0.1') or hasIpAddress('::1')")))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .build();
    }
}
