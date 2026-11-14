package team.codingforest.moyeota.user.passwordreset.presentation;

import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.common.exception.ErrorResponse;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.passwordreset.domain.ResetRateLimited;

@Order(-1)
@RestControllerAdvice(assignableTypes = PasswordResetController.class)
public class PasswordResetExceptionHandler {
    @ExceptionHandler(ResetRateLimited.class)
    public ResponseEntity<ErrorResponse> limited(ResetRateLimited e) {
        return ResponseEntity.status(429).cacheControl(CacheControl.noStore())
                .header("Retry-After", Long.toString(e.retryAfter()))
                .body(new ErrorResponse(e.getErrorCode().getCode(), e.getMessage()));
    }
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErrorResponse> unavailable(RuntimeException e) {
        var code = UserErrorCode.PASSWORD_RESET_UNAVAILABLE;
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse(code.getCode(), code.getMessage()));
    }
}
