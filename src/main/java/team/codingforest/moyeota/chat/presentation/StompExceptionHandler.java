package team.codingforest.moyeota.chat.presentation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;
import team.codingforest.moyeota.chat.presentation.dto.ErrorResponse;

import java.util.List;

@Slf4j
@ControllerAdvice(basePackages = "team.codingforest.moyeota.chat")
public class StompExceptionHandler {

    private static final String ERROR_DESTINATION = "/queue/errors";
    private static final String INVALID_REQUEST = "INVALID_REQUEST";
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    @MessageExceptionHandler(ChatException.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ErrorResponse handleChatException(ChatException e) {
        ChatErrorCode errorCode = e.getErrorCode();

        log.warn("STOMP 채팅 예외 code={} message={}", errorCode.name(), errorCode.getMessage());

        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ErrorResponse handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = toMessage(e.getBindingResult());

        log.warn("STOMP 요청 검증 실패 message={}", message);

        return new ErrorResponse(INVALID_REQUEST, message);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = ERROR_DESTINATION, broadcast = false)
    public ErrorResponse handleException(Exception e) {
        log.error("STOMP 처리 중 예외", e);

        return new ErrorResponse(INTERNAL_ERROR, "요청을 처리할 수 없습니다.");
    }

    private String toMessage(BindingResult bindingResult) {
        if (bindingResult == null) {
            return "잘못된 요청입니다.";
        }

        List<FieldError> fieldErrors = bindingResult.getFieldErrors();

        return fieldErrors.stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("잘못된 요청입니다.");
    }
}
