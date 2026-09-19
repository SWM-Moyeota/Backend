package team.codingforest.moyeota.searchhistory.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum SearchHistoryErrorCode implements ErrorCode {
    SEARCH_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "검색기록을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    SearchHistoryErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
