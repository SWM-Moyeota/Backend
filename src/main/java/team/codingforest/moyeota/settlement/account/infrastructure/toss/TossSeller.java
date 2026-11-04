package team.codingforest.moyeota.settlement.account.infrastructure.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 셀러 응답 중 우리가 쓰는 필드
 *
 * @param id          토스가 발급한 셀러 id
 * @param refSellerId 우리 쪽 셀러 참조 id
 * @param status      셀러 상태
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TossSeller(String id, String refSellerId, String status) {
}
