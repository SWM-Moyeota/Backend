package team.codingforest.moyeota.socket.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * 연결·구독 거절을 ERROR 프레임으로 알림, message 헤더에 고정 코드를, 본문에 코드와 설명을 JSON 으로 실음
 * SocketException 이 아닌 예외도 SERVER_UNAVAILABLE 로 보내 앱이 로그아웃하지 않고 다시 시도하게 함
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketErrorHandler extends StompSubProtocolErrorHandler {
    private final ObjectMapper objectMapper;

    @Override
    protected Message<byte[]> handleInternal(StompHeaderAccessor errorHeaderAccessor, byte[] errorPayload,
                                             @Nullable Throwable cause, @Nullable StompHeaderAccessor clientHeaderAccessor) {
        // 브로커가 보낸 ERROR 는 원인이 없음 - 그대로 둠
        if (cause == null) {
            return super.handleInternal(errorHeaderAccessor, errorPayload, null, clientHeaderAccessor);
        }

        SocketErrorCode code = errorCode(cause);
        errorHeaderAccessor.setMessage(code.name());
        errorHeaderAccessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        byte[] body = objectMapper.writeValueAsBytes(new ErrorBody(code.name(), code.getMessage()));

        return MessageBuilder.createMessage(body, errorHeaderAccessor.getMessageHeaders());
    }

    // 인터셉터에서 던진 예외는 MessageDeliveryException 에 감싸여 옴
    private SocketErrorCode errorCode(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof SocketException e) {
                return e.getErrorCode();
            }
        }

        log.warn("예상하지 못한 STOMP 처리 오류", cause);
        return SocketErrorCode.SERVER_UNAVAILABLE;
    }

    record ErrorBody(String code, String message) {
    }
}
