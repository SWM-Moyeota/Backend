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
 * 운영자 API(/api/v1/operator/**) 인증. X-Operator-Token 헤더를 설정값 moyeota.operator.token(환경 변수 OPERATOR_TOKEN)과 비교한다.
 * 운영자 계정·관리자 화면이 아직 없어 공유 비밀 하나로 막는 최소 장치다 - 계정 기반 인증이 생기면 바꾼다.
 *  - 설정값이 비어 있으면 운영자 API 가 꺼진 것으로 보고 모든 요청을 거절한다
 *  - 헤더가 없거나 틀리면 401 OPERATOR_UNAUTHORIZED(USER006). 어느 경우인지 응답으로 구분하지 않는다
 *  - 두 값을 SHA-256 으로 같은 길이로 만든 뒤 MessageDigest.isEqual 로 비교한다 - 비교 시간으로 토큰을 한 글자씩 맞혀 보지 못하게(길이도 새지 않게)
 *  - 토큰은 받은 값이든 설정값이든 로그에 남기지 않는다
 * 빈으로 등록하지 않는다 - 빈이면 서블릿 필터로도 자동 등록돼 모든 요청에 걸린다. SecurityConfig 의 운영자 체인에만 넣는다
 */
@Slf4j
public class OperatorTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Operator-Token";
    static final String ROLE = "OPERATOR";
    private static final String PRINCIPAL = "operator";

    // 설정값의 해시. 설정값이 비어 있으면 null - 모든 요청을 거절한다
    private final byte[] expectedDigest;
    private final JsonAuthenticationEntryPoint entryPoint;

    OperatorTokenFilter(String operatorToken, JsonAuthenticationEntryPoint entryPoint) {
        this.expectedDigest = operatorToken == null || operatorToken.isBlank() ? null : sha256(operatorToken.strip());
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        if (expectedDigest == null) {
            log.warn("운영자 토큰이 설정되지 않아 운영자 API 요청을 거절함 method={}, uri={}", request.getMethod(), request.getRequestURI());
            entryPoint.write(response, UserErrorCode.OPERATOR_UNAUTHORIZED);
            return;
        }

        String provided = request.getHeader(HEADER);

        if (provided == null || !MessageDigest.isEqual(expectedDigest, sha256(provided))) {
            log.warn("운영자 토큰이 없거나 틀려 거절함 method={}, uri={}, remoteAddr={}, headerPresent={}",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), provided != null);
            entryPoint.write(response, UserErrorCode.OPERATOR_UNAUTHORIZED);
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
            // 모든 JDK 가 갖춰야 하는 알고리즘이라 일어나지 않는다
            throw new IllegalStateException(e);
        }
    }
}
