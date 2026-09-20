package team.codingforest.moyeota.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * 요청 하나당 한 줄: method, path, status, durationMs, userId 를 key-value 로 남긴다.
 * 구조화 로깅(logging.structured.format)을 켜면 그대로 JSON 필드가 되어 Logstash → Elasticsearch 에서
 * 경로별 처리 시간과 오류 현황을 바로 집계할 수 있다.
 *
 * 요청 ID 는 MDC(requestId)에 실어 같은 요청 안의 다른 로그(외부 API 단계 시간 등)와 묶이게 하고,
 * 응답 헤더 X-Request-Id 로도 돌려줘 클라이언트 문의 시 로그를 찾을 수 있게 한다.
 *
 * SecurityContextHolderFilter 뒤에 두어야 chain 이 돌아온 뒤에도 인증 정보가 남아 있다(userId 기록용).
 */
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    /** 이 시간을 넘기면 WARN - Kibana 에서 level 만으로 느린 요청을 거를 수 있게 */
    static final long SLOW_THRESHOLD_MS = 1_000;

    private static final Set<String> SKIP_PATHS = Set.of("/health", "/prometheus");
    private static final Set<String> SKIP_PREFIXES = Set.of("/swagger-ui", "/v3/api-docs");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return SKIP_PATHS.contains(path) || SKIP_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.nanoTime();
        boolean unhandled = false;
        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            unhandled = true;   // 예외 핸들러를 벗어난 예외 - 상태 코드가 아직 안 찍혀 있으니 500 으로 기록
            throw e;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            int status = unhandled ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
            write(request, status, durationMs);
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private void write(HttpServletRequest request, int status, long durationMs) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        Long userId = currentUserId();

        LoggingEventBuilder event = (durationMs >= SLOW_THRESHOLD_MS || status >= 500) ? log.atWarn() : log.atInfo();
        event = event.addKeyValue("method", method)
                .addKeyValue("path", path)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs);
        if(query != null) event = event.addKeyValue("query", query);
        if(userId != null) event = event.addKeyValue("userId", userId);

        event.log("{} {} -> {} ({}ms)", method, path, status, durationMs);
    }

    private String resolveRequestId(HttpServletRequest request) {
        String fromClient = request.getHeader(REQUEST_ID_HEADER);
        if(fromClient != null && !fromClient.isBlank() && fromClient.length() <= 64) return fromClient;
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal.userId();
        }
        return null;
    }
}
