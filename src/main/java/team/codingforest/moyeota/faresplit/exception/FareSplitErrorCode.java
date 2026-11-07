package team.codingforest.moyeota.faresplit.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum FareSplitErrorCode implements ErrorCode {
    // 방 - 응답 코드가 바뀌지 않게 MatchingErrorCode 와 같은 이름·상태·메시지를 쓴다
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 방입니다."),
    NOT_PARTY_MEMBER(HttpStatus.FORBIDDEN, "해당 방에 참여하고 있지 않습니다."),

    // 정산
    FARE_SPLIT_NOT_FOUND(HttpStatus.NOT_FOUND, "이 방의 택시비 정산이 없습니다."),
    FARE_SPLIT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 택시비 정산을 시작한 방입니다."),
    NOT_FARE_SPLIT_PARTICIPANT(HttpStatus.FORBIDDEN, "이 택시비 정산에 참여하지 않았습니다."),
    NOT_FARE_SPLIT_PAYER(HttpStatus.FORBIDDEN, "택시비를 받는 사람만 할 수 있습니다."),
    NOT_FARE_SPLIT_SENDER(HttpStatus.FORBIDDEN, "택시비를 보내는 사람만 할 수 있습니다."),
    NOT_A_RIDER(HttpStatus.FORBIDDEN, "함께 탄 사람만 택시비를 정산할 수 있습니다."),
    PARTY_NOT_FINISHED(HttpStatus.CONFLICT, "동승이 끝난 방에서만 택시비를 정산할 수 있습니다."),
    NOT_ENOUGH_RIDERS(HttpStatus.CONFLICT, "함께 탄 사람이 2명 이상이어야 택시비를 나눌 수 있습니다."),
    INVALID_FARE(HttpStatus.BAD_REQUEST, "택시비는 1,000원~500,000원이어야 합니다."),
    INVALID_ACCOUNT_NUMBER(HttpStatus.BAD_REQUEST, "계좌번호는 숫자 10~16자리여야 합니다."),
    FARE_SPLIT_TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "이 정산에서 택시비를 보내는 사람이 아닙니다."),
    FARE_SPLIT_CLOSED(HttpStatus.CONFLICT, "이미 끝난 택시비 정산입니다."),
    FARE_SPLIT_ALREADY_STARTED(HttpStatus.CONFLICT, "송금이 시작된 정산은 고치거나 취소할 수 없습니다."),
    TRANSFER_NOT_SENT(HttpStatus.CONFLICT, "보냈다고 표시된 송금만 못 받았다고 되돌릴 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    FareSplitErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
