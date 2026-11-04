package team.codingforest.moyeota.settlement.common.toss;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 토스 지급 요청 한 건. 토스는 이 객체의 배열을 받음
 *
 * @param refPayoutId            우리 지급 키. 다시 쓸 수 없음
 * @param destination            받을 토스 셀러 id
 * @param scheduleType           EXPRESS 바로지급 / SCHEDULED 예약지급
 * @param amount                 지급 금액
 * @param transactionDescription 입금 내역에 보일 문구, 7자 이하
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record TossPayoutRequest(String refPayoutId, String destination, String scheduleType, Amount amount, String transactionDescription) {

    record Amount(String currency, int value) {
    }
}
