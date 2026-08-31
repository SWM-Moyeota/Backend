package team.codingforest.moyeota.dispatch.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum DispatchErrorCode implements ErrorCode {
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 방입니다."),
    CALL_CLOSED(HttpStatus.CONFLICT, "호출받지 않았거나 이미 마감된 콜입니다."),
    DRIVER_CANNOT_RECEIVE(HttpStatus.CONFLICT, "현재 콜을 받을 수 없는 상태입니다."),
    DRIVER_ALREADY_RIDING(HttpStatus.CONFLICT, "이미 운행중인 기사입니다."),
    DRIVER_NOT_ASSIGNED(HttpStatus.CONFLICT, "아직 기사가 배정되지 않았습니다."),
    NOT_AWAITING_PICKUP(HttpStatus.CONFLICT, "픽업 대기 중인 방이 아닙니다."),
    NOT_PARTY_MEMBER(HttpStatus.FORBIDDEN, "해당 방에 참여하고 있지 않습니다."),
    DRIVER_LOCATION_UNAVAILABLE(HttpStatus.NOT_FOUND, "현재 기사님의 위치를 확인할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    DispatchErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
