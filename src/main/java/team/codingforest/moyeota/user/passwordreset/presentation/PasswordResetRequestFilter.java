package team.codingforest.moyeota.user.passwordreset.presentation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import team.codingforest.moyeota.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.passwordreset.domain.ResetRequests;
import team.codingforest.moyeota.user.passwordreset.domain.ResetRateLimited;
import java.io.IOException;

/** 전달 헤더를 직접 신뢰하지 않는다. 프록시 환경은 서버의 trusted proxy 설정으로 remoteAddr를 복원한다. */
@Order(-110) @RequiredArgsConstructor
public class PasswordResetRequestFilter extends OncePerRequestFilter {
    private final ResetRequests requests;
    private final FieldHasher hasher;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/v1/auth/password-reset/")
                || request.getMethod().equals("OPTIONS");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            requests.checkClient(hasher.hash("PASSWORD_RESET:IP:" + request.getRemoteAddr()));
        } catch (UserException e) {
            response.setStatus(e.getErrorCode().getHttpStatus().value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            if (e instanceof ResetRateLimited rate) response.setHeader("Retry-After", Long.toString(rate.retryAfter()));
            // enum의 고정 문자열만 출력한다. 예외 원문이나 입력값은 포함하지 않는다.
            response.getWriter().write("{\"code\":\"" + e.getErrorCode().getCode()
                    + "\",\"message\":\"" + e.getErrorCode().getMessage() + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
