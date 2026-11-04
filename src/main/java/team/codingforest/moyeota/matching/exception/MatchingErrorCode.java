package team.codingforest.moyeota.matching.exception;

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
    NOT_PARTY_HOST(HttpStatus.FORBIDDEN, "방장만 수행할 수 있습니다."),
    PARTY_NOT_RECRUITING(HttpStatus.CONFLICT, "모집 중인 방이 아닙니다."),
    PARTY_TIME_CONFLICT(HttpStatus.CONFLICT, "출발 시각이 겹치는 예약 방이 있습니다."),
    NOT_ENOUGH_MEMBERS(HttpStatus.CONFLICT, "함께 탈 인원이 부족합니다."),
    NOT_REALTIME_PARTY(HttpStatus.CONFLICT, "실시간 방에서만 할 수 있습니다."),
    ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 약속 시간을 확정한 방입니다."),
    CANNOT_LEAVE_CONFIRMED_PARTY(HttpStatus.CONFLICT, "약속이 확정된 방은 나갈 수 없습니다."),
    RESERVATION_NOT_SUPPORTED(HttpStatus.CONFLICT, "지금은 예약 방을 만들 수 없습니다."),

    // 출발지 도착·출발
    PARTY_NOT_CONFIRMED(HttpStatus.CONFLICT, "약속 시간이 확정되지 않은 방입니다."),
    MEETING_NOT_STARTED(HttpStatus.CONFLICT, "아직 약속 시간이 되지 않았습니다."),
    TOO_FAR_FROM_DEPARTURE(HttpStatus.BAD_REQUEST, "출발지에서 100m 안에 있어야 합니다."),
    NOT_ARRIVED(HttpStatus.CONFLICT, "출발지에 도착한 사람만 출발할 수 있습니다."),
    USE_DEPART_FOR_CONFIRMED_PARTY(HttpStatus.CONFLICT, "약속이 확정된 방은 출발 버튼으로 끝냅니다."),

    // 노쇼 제재·이의 신청
    NO_SHOW_RESTRICTED(HttpStatus.FORBIDDEN, "노쇼로 이용이 제한된 상태입니다."),
    NOT_NO_SHOW(HttpStatus.CONFLICT, "이 방에서 노쇼로 기록되지 않았습니다."),
    ALREADY_OBJECTED(HttpStatus.CONFLICT, "이미 이의를 신청한 방입니다."),
    OBJECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 이의 신청입니다."),
    OBJECTION_ALREADY_RESOLVED(HttpStatus.CONFLICT, "이미 처리된 이의 신청입니다."),

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
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "조회 개수는 1~50 이어야 합니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "커서가 올바르지 않습니다."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "조회 기간이 올바르지 않습니다."),

    // 방 생성 입력 검증
    SAME_DEPARTURE_DESTINATION(HttpStatus.BAD_REQUEST, "출발지와 도착지가 같습니다."),
    INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "방 정원은 1~3명이어야 합니다."),
    INVALID_RADIUS(HttpStatus.BAD_REQUEST, "허용 반경이 아닙니다."),
    INVALID_DEPARTURE_TIME(HttpStatus.BAD_REQUEST, "출발 시각은 지금부터 7일 이내여야 합니다."),
    INVALID_MEETING_TIME(HttpStatus.BAD_REQUEST, "약속 시간은 지금부터 1시간 이내여야 합니다."),
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
