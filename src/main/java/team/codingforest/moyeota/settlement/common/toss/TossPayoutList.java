package team.codingforest.moyeota.settlement.common.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 지급 요청 응답의 entityBody. 요청한 순서대로 지급이 담김
 *
 * @param items 지급 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TossPayoutList(List<TossPayout> items) {
}
