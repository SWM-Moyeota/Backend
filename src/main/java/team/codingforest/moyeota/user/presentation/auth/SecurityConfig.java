package team.codingforest.moyeota.user.presentation.auth;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import team.codingforest.moyeota.common.logging.RequestLoggingFilter;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.application.AuthService;

import java.util.List;

/**
 * JWT 전용 Security. 세션 없음, CSRF/폼로그인/basic 끔.
 * /api/v1/auth/** 와 WebSocket 핸드셰이크만 열고 나머지는 토큰 필수. @CurrentUser 리졸버도 여기서 등록.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {
    static {
        // 토큰에서 주입되는 파라미터 - Swagger 스펙에 쿼리 파라미터로 잡히면 안 된다
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }


    private final AuthService authService;
    private final JsonAuthenticationEntryPoint entryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**", "/api/v1/config", "/ws-chat/**", "/health").permitAll()
                        .requestMatchers("/prometheus").permitAll()   // actuator 메트릭. loadtest 프로필에서만 exposure 에 포함되고 그 외엔 404
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(authService, entryPoint), AuthorizationFilter.class)
                // 요청 로그. SecurityContextHolderFilter 바로 뒤라 JWT 401 도 잡히고, 돌아온 뒤 userId 도 읽힌다
                .addFilterAfter(new RequestLoggingFilter(), SecurityContextHolderFilter.class)
                .build();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
