package team.codingforest.moyeota.user.social.presentation;

import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.common.exception.ErrorResponse;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;

/** 저장소 오류의 SQL·파라미터를 응답이나 로그에 담지 않는다. */
@Order(0)
@RestControllerAdvice(assignableTypes={AppleAuthController.class,LoginMethodsController.class,SocialAuthController.class})
public class SocialStorageExceptionHandler {
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> unavailable() {
        var error=UserErrorCode.SOCIAL_UNAVAILABLE;
        return ResponseEntity.status(error.getHttpStatus()).body(new ErrorResponse(error.getCode(),error.getMessage()));
    }
}
