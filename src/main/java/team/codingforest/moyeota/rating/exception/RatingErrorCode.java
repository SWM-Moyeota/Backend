package team.codingforest.moyeota.rating.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum RatingErrorCode implements ErrorCode {
    RATING_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 평점입니다."),
    RATING_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록한 평점입니다."),
    INVALID_RATING(HttpStatus.BAD_REQUEST, "평점은 1점부터 5점까지 입력할 수 있습니다."),
    SELF_RATING_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신에게 평점을 줄 수 없습니다."),
    RATING_SUMMARY_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "평점 집계 정보를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    RatingErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
