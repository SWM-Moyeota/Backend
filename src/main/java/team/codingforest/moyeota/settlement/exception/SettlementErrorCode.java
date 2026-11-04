package team.codingforest.moyeota.settlement.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum SettlementErrorCode implements ErrorCode {
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 정산서입니다."),

    INVALID_SETTLEMENT_COMMAND(HttpStatus.BAD_REQUEST, "정산 기록 요청이 올바르지 않습니다."),
    INVALID_TOTAL_FARE(HttpStatus.BAD_REQUEST, "운행 금액은 0보다 커야 합니다."),
    INVALID_PLATFORM_PROMOTION(HttpStatus.BAD_REQUEST, "프로모션 금액은 0 이상이어야 합니다."),

    INVALID_SETTLEMENT_STATUS(HttpStatus.CONFLICT, "현재 정산 상태에서 할 수 없는 처리입니다."),
    INVALID_PAYOUT_STATUS(HttpStatus.CONFLICT, "현재 지급 상태에서 할 수 없는 처리입니다."),
    INVALID_PAYOUT_AMOUNT(HttpStatus.CONFLICT, "지급 금액은 0보다 커야 합니다."),
    INVALID_PAYOUT_ACCOUNT(HttpStatus.BAD_REQUEST, "지급 계좌 정보가 올바르지 않습니다."),
    INVALID_ACCOUNT_NUMBER(HttpStatus.BAD_REQUEST, "계좌번호는 숫자만 14자리 이하로 입력해야 합니다."),
    INVALID_ACCOUNT_HOLDER(HttpStatus.BAD_REQUEST, "예금주는 1자 이상 50자 이하여야 합니다."),
    PAYOUT_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 지급 계좌가 없습니다."),
    PAYOUT_ACCOUNT_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 등록된 지급 계좌입니다."),
    PAYOUT_ACCOUNT_REGISTERING(HttpStatus.CONFLICT, "지급 계좌 등록을 처리하고 있습니다. 잠시 후 다시 시도해 주세요."),
    PAYOUT_ACCOUNT_REJECTED(HttpStatus.BAD_REQUEST, "결제사가 지급 계좌 등록을 거절했습니다. 사업자 정보와 계좌를 확인해 주세요."),
    PAYOUT_ACCOUNT_LOCKED(HttpStatus.CONFLICT, "지금은 지급 계좌를 바꿀 수 없습니다."),
    PAYOUT_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "결제사 응답이 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    SettlementErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
