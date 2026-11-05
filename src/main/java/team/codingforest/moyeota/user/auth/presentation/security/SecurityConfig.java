package team.codingforest.moyeota.user.auth.presentation.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.auth.application.AuthService;

import java.util.List;

/**
 * JWT 전용 Security. 세션 없음, CSRF/폼로그인/basic 끔.
 * /api/v1/auth/** 와 WebSocket 핸드셰이크만 열고 나머지는 토큰 필수. @CurrentUser 리졸버도 여기서 등록.
 * 운영자 API(/api/v1/operator/**)만 JWT 대신 X-Operator-Token 헤더를 보는 별도 체인으로 막는다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {
    static {
        // 토큰에서 주입되는 파라미터 - Swagger 스펙에 쿼리 파라미터로 잡히면 안 된다
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }


    static final String OPERATOR_PATH = "/api/v1/operator/**";
    static final String INTERNAL_PATH = "/internal/chat/chat-rooms/**";

    private final AuthService authService;
    private final JsonAuthenticationEntryPoint entryPoint;

    /**
     * 운영자 API 전용 체인 - 사용자 JWT 필터가 없어 Bearer 토큰으로는 들어올 수 없고, OperatorTokenFilter 를 통과해야 OPERATOR 권한이 붙는다.
     * 아래 사용자 체인은 모든 요청에 걸리는 체인이라 이 체인이 먼저 골라지도록 순서를 앞에 둔다.
     * 컨트롤러 인터셉터가 아니라 필터 체인에 두는 까닭: 매처에 걸린 경로는 무조건 권한을 요구해(기본 거부) 새 운영자 API 를 추가해도 빠뜨릴 수 없다
     */
    @Bean
    @Order(1)
    public SecurityFilterChain operatorSecurityFilterChain(HttpSecurity http, @Value("${moyeota.operator.token:}") String operatorToken) throws Exception {
        return http
                .securityMatcher(OPERATOR_PATH)
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(OperatorTokenFilter.ROLE))
                .addFilterBefore(new OperatorTokenFilter(operatorToken, entryPoint), AuthorizationFilter.class)
                .build();
    }

    /**
     * 소켓 서버 전용 내부 API 체인 - 사용자 JWT 없이 부르므로 InternalTokenFilter 를 통과해야 INTERNAL 권한이 붙음
     * 사용자 체인보다 먼저 골라지도록 순서를 앞에 둠, /internal/chat/principal 은 사용자 JWT 로 확인하므로 여기 넣지 않음
     */
    @Bean
    @Order(2)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http, @Value("${moyeota.internal.token:}") String internalToken) throws Exception {
        return http
                .securityMatcher(INTERNAL_PATH)
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(InternalTokenFilter.ROLE))
                .addFilterBefore(new InternalTokenFilter(internalToken, entryPoint), AuthorizationFilter.class)
                .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()     // ASYNC -> emitter complete 메서드 호출할때 필터에 다시 탐
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**", "/api/v1/config","/ws-chat/**", "/health", "/prometheus", "/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(authService, entryPoint), AuthorizationFilter.class)
                .build();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
