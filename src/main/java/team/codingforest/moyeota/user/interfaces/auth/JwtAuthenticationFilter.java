package team.codingforest.moyeota.user.interfaces.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.application.AuthService;
import team.codingforest.moyeota.user.domain.exception.UserException;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer <access> 검증 후 SecurityContext 에 사용자(AuthenticatedPrincipal) 저장.
 * 헤더 없으면 익명으로 통과(보호 여부는 SecurityConfig URL 규칙). 토큰 깨졌으면 바로 401.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final JsonAuthenticationEntryPoint entryPoint;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            AuthenticatedPrincipal principal = authService.authenticate(header.substring(BEARER_PREFIX.length()).trim());
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        } catch (UserException e) {
            entryPoint.write(response, e.getErrorCode());
            return;
        }

        chain.doFilter(request, response);
    }
}
