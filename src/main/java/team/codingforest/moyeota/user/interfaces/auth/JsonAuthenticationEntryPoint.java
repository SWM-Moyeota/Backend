package team.codingforest.moyeota.user.interfaces.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.common.exception.ErrorResponse;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 토큰 없이 보호된 URL 접근 시 Security 가 호출. 필터 체인은 @RestControllerAdvice 밖이라
 * GlobalExceptionHandler 와 같은 {code, message} 포맷으로 직접 401 을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        write(response, UserErrorCode.UNAUTHORIZED);
    }

    void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(errorCode.getCode(), errorCode.getMessage())));
    }
}
