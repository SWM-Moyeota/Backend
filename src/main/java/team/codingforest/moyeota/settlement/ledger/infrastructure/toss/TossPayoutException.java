package team.codingforest.moyeota.settlement.ledger.infrastructure.toss;

import lombok.Getter;

/**
 * 토스 지급대행 API 가 에러를 돌려주거나 응답을 읽을 수 없을 때
 */
@Getter
public class TossPayoutException extends RuntimeException {
    private final int httpStatus;
    private final String code;
    private final String traceId;

    public TossPayoutException(int httpStatus, String code, String message, String traceId, Throwable cause) {
        super("토스 지급대행 실패 httpStatus=" + httpStatus + ", code=" + code + ", message=" + message + ", traceId=" + traceId, cause);
        this.httpStatus = httpStatus;
        this.code = code;
        this.traceId = traceId;
    }

    static TossPayoutException of(int httpStatus, TossResponse response) {
        TossResponse.Error error = response.error();
        return new TossPayoutException(httpStatus, error == null ? null : error.code(),
                error == null ? "에러 객체 없음" : error.message(), response.traceId(), null);
    }

    static TossPayoutException unreadable(int httpStatus, String reason, Throwable cause) {
        return new TossPayoutException(httpStatus, null, reason, null, cause);
    }
}