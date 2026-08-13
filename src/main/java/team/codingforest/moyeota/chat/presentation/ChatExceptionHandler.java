package team.codingforest.moyeota.chat.presentation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;
import team.codingforest.moyeota.chat.presentation.dto.ErrorResponse;

import java.util.List;

@Slf4j
@RestControllerAdvice(basePackages = "team.codingforest.moyeota.chat")
public class ChatExceptionHandler {

    private static final String INVALID_REQUEST = "INVALID_REQUEST";

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ErrorResponse> handleChatException(ChatException e) {
        ChatErrorCode errorCode = e.getErrorCode();

        log.warn("채팅 예외 code={} message={}", errorCode.name(), errorCode.getMessage());

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(new ErrorResponse(errorCode.name(), errorCode.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = toMessage(e.getBindingResult().getFieldErrors());

        log.warn("요청 검증 실패 message={}", message);

        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(INVALID_REQUEST, message));
    }

    private String toMessage(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("잘못된 요청입니다");
    }
}
