package team.codingforest.moyeota.matching.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum MatchingErrorCode implements ErrorCode {
    // 조회
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 방입니다."),
    ASSIGNED_DRIVER_NOT_FOUND(HttpStatus.NOT_FOUND, "기사 정보를 찾을 수 없습니다."),

    // 참여/이탈
    ALREADY_JOINED_OTHER_PARTY(HttpStatus.CONFLICT, "이미 참여 중인 방이 있습니다."),
    ALREADY_JOINED_PARTY(HttpStatus.CONFLICT, "이미 참여한 방입니다."),
    PARTY_CLOSED(HttpStatus.CONFLICT, "마감된 방입니다."),
    PARTY_FULL(HttpStatus.CONFLICT, "정원이 가득 찬 방입니다."),
    NOT_PARTY_MEMBER(HttpStatus.FORBIDDEN, "해당 방에 참여하고 있지 않습니다."),
    PARTY_NOT_RECRUITING(HttpStatus.CONFLICT, "모집 중인 방이 아닙니다."),

    // 매칭·배정·운행 상태
    PARTY_NOT_COMPLETED(HttpStatus.CONFLICT, "정원이 다 차지 않은 방입니다."),
    PARTY_NOT_MATCHING(HttpStatus.CONFLICT, "매칭 중인 방이 아닙니다."),
    DRIVER_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "이미 기사가 배정된 방입니다."),
    DRIVER_NOT_ASSIGNED(HttpStatus.CONFLICT, "아직 기사가 배정되지 않았습니다."),
    NOT_ASSIGNED_DRIVER(HttpStatus.FORBIDDEN, "이 방에 배정된 기사가 아닙니다."),
    NOT_AWAITING_PICKUP(HttpStatus.CONFLICT, "탑승 대기 상태가 아닙니다."),
    NOT_RIDING(HttpStatus.CONFLICT, "운행 중이 아닙니다."),

    // 지도 영역 조회 검증
    INVALID_MAP_BOUNDS(HttpStatus.BAD_REQUEST, "영역 좌표가 올바르지 않습니다."),

    // 방 생성 입력 검증
    SAME_DEPARTURE_DESTINATION(HttpStatus.BAD_REQUEST, "출발지와 도착지가 같습니다."),
    INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "방 정원은 1~3명이어야 합니다."),
    INVALID_RADIUS(HttpStatus.BAD_REQUEST, "허용 반경이 아닙니다."),
    OUT_OF_SERVICE_AREA(HttpStatus.BAD_REQUEST, "서비스 지역을 벗어난 좌표입니다."),

    // 경로(네이버)
    INVALID_ROUTE_ESTIMATE(HttpStatus.BAD_REQUEST, "경로 정보가 올바르지 않습니다."),
    ROUTE_NOT_FOUND(HttpStatus.BAD_REQUEST, "경로를 찾을 수 없습니다."),
    ROUTE_SEARCH_FAILED(HttpStatus.BAD_GATEWAY, "경로 조회에 실패했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    MatchingErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
