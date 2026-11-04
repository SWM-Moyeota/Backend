package team.codingforest.moyeota.settlement.common.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

/**
 * 토스 API v2 응답 봉투. 성공이면 entityBody, 실패면 error 가 채워짐
 *
 * @param version    API 버전
 * @param traceId    토스 문의 때 쓰는 추적 id
 * @param entityType 응답 객체 종류
 * @param entityBody 응답 객체
 * @param error      에러 객체
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TossResponse(String version, String traceId, String entityType, JsonNode entityBody, Error error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Error(String code, String message) {
    }
}
