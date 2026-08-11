package team.codingforest.moyeota.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import team.codingforest.moyeota.auth.jwt.JWTFilter;
import team.codingforest.moyeota.auth.jwt.JWTUtil;
import team.codingforest.moyeota.auth.oauth2.CustomSuccessHandler;
import team.codingforest.moyeota.auth.service.CustomOAuth2UserService;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomSuccessHandler customSuccessHandler;
    private final JWTUtil jwtUtil;
    private final Environment environment;
    private final String frontendOrigin;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService, CustomSuccessHandler customSuccessHandler,
                          JWTUtil jwtUtil, Environment environment,
                          @Value("${app.frontend-origin}") String frontendOrigin) {

        this.customOAuth2UserService = customOAuth2UserService;
        this.customSuccessHandler = customSuccessHandler;
        this.jwtUtil = jwtUtil;
        this.environment = environment;
        this.frontendOrigin = frontendOrigin;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        //CORS. 토큰을 헤더로 주고받으므로 쿠키(credentials)는 쓰지 않는다.
        http
                .cors(corsCustomizer -> corsCustomizer.configurationSource(new CorsConfigurationSource() {

                    @Override
                    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {

                        CorsConfiguration configuration = new CorsConfiguration();

                        configuration.setAllowedOrigins(List.of(frontendOrigin));
                        configuration.setAllowedMethods(Collections.singletonList("*"));
                        configuration.setAllowedHeaders(Collections.singletonList("*"));
                        configuration.setMaxAge(3600L);

                        return configuration;
                    }
                }));

        //csrf disable
        //토큰을 쿠키가 아니라 헤더로 보내므로 브라우저가 인증정보를 자동 첨부하지 않는다.
        //즉 CSRF의 전제 자체가 성립하지 않아 꺼도 된다.
        http
                .csrf((auth) -> auth.disable());

        //From 로그인 방식 disable
        http
                .formLogin((auth) -> auth.disable());

        //HTTP Basic 인증 방식 disable
        http
                .httpBasic((auth) -> auth.disable());

        //JWTFilter 추가
        http
                .addFilterBefore(new JWTFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);

        //oauth2
        http
                .oauth2Login((oauth2) -> oauth2
                        .userInfoEndpoint((userInfoEndpointConfig) -> userInfoEndpointConfig
                                .userService(customOAuth2UserService))
                        .successHandler(customSuccessHandler)
                );

        //인증 실패 시 401을 준다.
        //기본값은 구글 로그인으로의 302 리다이렉트인데, 프론트가 fetch로 API를 부르면
        //리다이렉트를 따라가다 CORS로 실패해서 "왜 401이 아니지?" 하게 된다.
        //로그인 시작은 프론트가 /oauth2/authorization/google 로 직접 이동시키는 것으로 처리한다.
        http
                .exceptionHandling((exception) -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"unauthorized\"}");
                        }));

        //경로별 인가 작업
        http
                .authorizeHttpRequests((auth) -> {
                    //AuthController의 @RequestMapping이 /api/v1/auth 이므로 패턴도 v1을 포함해야 한다.
                    auth.requestMatchers("/", "/api/v1/auth/**").permitAll();

                    //예전 방식(프래그먼트로 토큰을 바로 넘기는) 구글 로그인 시작점.
                    //AuthV2Controller가 담당하며, v1과 마찬가지로 로그인 전에 부르는 경로라 열어둔다.
                    auth.requestMatchers("/api/v2/auth/**").permitAll();

                    //Swagger UI와 OpenAPI 문서. 열어주지 않으면 인증 필요 경로로 취급된다.
                    auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();

                    /*
                    /error 도 열어야 한다. 안 열면 우리가 내는 에러 응답이 전부 401로 바뀐다.

                    컨트롤러가 400(검증 실패)이나 409(아이디 중복)를 내면 톰캣이 그 응답을 그대로 보내지 않고
                    /error 로 한 번 더 포워딩해서 본문을 만든다. 그런데 이 포워딩도 시큐리티 필터를 다시 타므로
                    (스프링 부트가 필터를 REQUEST/ASYNC/ERROR 디스패치에 모두 등록한다)
                    /error 가 anyRequest().authenticated() 에 걸려 거부되고,
                    authenticationEntryPoint가 원래 상태코드를 401 {"error":"unauthorized"} 로 덮어써 버린다.

                    그래서 회원가입이 실패했을 때 "무엇이 잘못됐는지"가 아니라 엉뚱한 401만 보였다.
                    */
                    auth.requestMatchers("/error").permitAll();

                    //H2 콘솔은 DispatcherServlet이 아닌 전용 서블릿이 처리하므로
                    //문자열 패턴("/h2-console/**")으로는 매칭되지 않는다. 전용 매처를 써야 함.
                    //다만 toH2Console() 매처는 매칭 시점에 H2ConsoleProperties 빈을 찾으므로,
                    //H2 콘솔이 꺼진 프로필(=postgres)에서 등록하면 모든 요청의 인가 검사가 터진다.
                    //콘솔 자동설정과 동일한 조건으로 켜져 있을 때만 등록한다.
                    if (environment.getProperty("spring.h2.console.enabled", Boolean.class, false)) {
                        auth.requestMatchers(PathRequest.toH2Console()).permitAll();
                    }

                    auth.requestMatchers("/my").hasRole("USER");
                    auth.anyRequest().authenticated();
                });

        //H2 콘솔이 iframe을 사용하므로 same-origin을 허용
        http
                .headers((headers) -> headers
                        .frameOptions((frameOptions) -> frameOptions.sameOrigin()));

        //세션 설정 : STATELESS
        http
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        //로그아웃은 POST /api/auth/logout 이 담당한다(refresh를 DB에서 삭제).
        //쿠키를 안 쓰므로 Security의 logout 필터로 지울 것이 없다.

        return http.build();
    }
}
