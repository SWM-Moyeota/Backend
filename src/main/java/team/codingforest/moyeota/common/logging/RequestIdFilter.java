package team.codingforest.moyeota.common.logging;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 *  요청 진입점. 요청마다 req_id 를 정해 MDC 와 응답 헤더(X-Request-Id)에 싣고, 끝나면 한 줄 요약(access log)을 남긴다.
 *  시큐리티 필터보다 앞에 둬서 401/403 응답에도 req_id 가 붙는다.
 *  클라이언트가 X-Request-Id 를 보내면 그대로 쓴다(앱 → 서버 로그 연결). 형식이 이상하면 새로 만든다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");
    /** 주기적으로 찍혀 INFO 로 남기면 로그만 차지하는 경로 */
    private static final Set<String> QUIET_PATHS = Set.of("/health", "/prometheus");
    private static final TimeBasedEpochGenerator UUID_V7 = Generators.timeBasedEpochGenerator();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        long startedAt = System.nanoTime();
        boolean failed = false;

        MDC.put(LogFields.REQUEST_ID, requestId);
        response.setHeader(LogFields.REQUEST_ID_HEADER, requestId);

        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            failed = true;
            throw e;
        } finally {
            logCompleted(request, failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus(), startedAt);
            MDC.clear();   // user_id 등 이 요청에서 쌓인 것까지 전부. 스레드가 풀로 돌아가므로 반드시
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String fromClient = request.getHeader(LogFields.REQUEST_ID_HEADER);
        if (fromClient != null && SAFE_REQUEST_ID.matcher(fromClient).matches()) {
            return fromClient;
        }
        return UUID_V7.generate().toString();   // 시간순 정렬되는 v7 - 로그 저장소에서 붙어 나온다
    }

    /** 쿼리스트링은 남기지 않는다 - 토큰·검색어 같은 값이 섞일 수 있다 */
    private void logCompleted(HttpServletRequest request, int status, long startedAt) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        String path = request.getRequestURI();

        LoggingEventBuilder event = QUIET_PATHS.contains(path) ? log.atDebug() : log.atInfo();
        event.addKeyValue(LogFields.EVENT, "http_request_completed")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("path", path)
                .addKeyValue("status", status)
                .addKeyValue("duration_ms", durationMs)
                .log("{} {} {} {}ms", request.getMethod(), path, status, durationMs);
    }
}
