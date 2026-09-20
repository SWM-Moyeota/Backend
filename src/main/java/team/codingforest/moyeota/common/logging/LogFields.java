package team.codingforest.moyeota.common.logging;

/**
 *  구조화 로그의 공통 필드 이름. MDC 에 넣으면 JSON 로그의 최상위 필드로 나간다.
 *  검색 예: level=ERROR AND user_id=1234 / req_id=... 로 한 요청의 전 과정 재구성
 */
public final class LogFields {
    /** 요청 하나를 식별. 진입 시 부여하고 응답 헤더로도 돌려준다 */
    public static final String REQUEST_ID = "req_id";
    /** 인증된 사용자 id. 토큰 검증 직후 채운다 */
    public static final String USER_ID = "user_id";
    /** 무슨 일이 일어났는지 - object_action 형식 (예: http_request_completed) */
    public static final String EVENT = "event";

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private LogFields() {
    }
}
