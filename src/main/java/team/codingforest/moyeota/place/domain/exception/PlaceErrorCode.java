package team.codingforest.moyeota.place.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum PlaceErrorCode implements ErrorCode {
    FAVORITE_PLACE_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 장소입니다."),
    FAVORITE_PLACE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "자주가는 장소는 최대 10개까지만 등록할 수 있습니다."),
    INVALID_PLACE_NAME(HttpStatus.BAD_REQUEST, "장소 이름은 필수입니다."),
    SEARCH_QUERY_EMPTY(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요."),
    PLACE_SEARCH_FAILED(HttpStatus.BAD_GATEWAY, "장소 검색에 실패했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    PlaceErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
