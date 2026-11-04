package team.codingforest.moyeota.settlement.common.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 지급 응답 중 우리가 쓰는 필드
 *
 * @param id          토스가 발급한 지급 id
 * @param refPayoutId 우리 지급 키
 * @param status      REQUESTED, IN_PROGRESS, COMPLETED, FAILED, REJECTED, CANCELED, DELETED
 * @param error       실패했을 때만 있음
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TossPayout(String id, String refPayoutId, String status, Error error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Error(String code, String message) {
    }
}
