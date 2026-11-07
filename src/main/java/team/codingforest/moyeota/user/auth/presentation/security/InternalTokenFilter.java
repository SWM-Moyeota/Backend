package team.codingforest.moyeota.user.auth.presentation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 소켓 서버 전용 내부 API(/internal/chat/chat-rooms/**) 인증, X-Internal-Token 헤더를 moyeota.internal.token 과 비교함
 * ALB 의 /internal/* 차단이 빠지거나 VPC 안에서 바로 불러도 참여 여부를 알아낼 수 없게 한 겹 더 막음
 * 설정값이 비어 있으면 모든 요청을 거절함 - 토큰을 넣지 않은 환경(prod)은 기본으로 닫힘
 * 두 값을 SHA-256 으로 같은 길이로 만든 뒤 MessageDigest.isEqual 로 비교함, 토큰은 로그에 남기지 않음
 * 빈으로 등록하지 않음 - 빈이면 서블릿 필터로도 자동 등록돼 모든 요청에 걸림
 */
@Slf4j
public class InternalTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Token";
    static final String ROLE = "INTERNAL";
    private static final String PRINCIPAL = "socket-server";

    // 설정값의 해시, 비어 있으면 null - 모든 요청을 거절함
    private final byte[] expectedDigest;
    private final JsonAuthenticationEntryPoint entryPoint;

    InternalTokenFilter(String internalToken, JsonAuthenticationEntryPoint entryPoint) {
        this.expectedDigest = internalToken == null || internalToken.isBlank() ? null : sha256(internalToken.strip());
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (expectedDigest == null) {
            log.warn("내부 토큰이 설정되지 않아 내부 API 요청을 거절함 uri={}", request.getRequestURI());
            entryPoint.write(response, UserErrorCode.INTERNAL_UNAUTHORIZED);
            return;
        }

        String provided = request.getHeader(HEADER);

        if (provided == null || !MessageDigest.isEqual(expectedDigest, sha256(provided))) {
            log.warn("내부 토큰이 없거나 틀려 거절함 uri={}, remoteAddr={}, headerPresent={}",
                    request.getRequestURI(), request.getRemoteAddr(), provided != null);
            entryPoint.write(response, UserErrorCode.INTERNAL_UNAUTHORIZED);
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
        SecurityContextHolder.setContext(context);

        chain.doFilter(request, response);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // 모든 JDK 가 갖춰야 하는 알고리즘이라 일어나지 않음
            throw new IllegalStateException(e);
        }
    }
}
