package team.codingforest.moyeota.settlement.account.infrastructure.toss;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 토스 셀러 수정 요청 본문. 토스 수정은 부분 업데이트라 null 로 보내면 그 필드가 지워지므로 계좌만 담고 null 은 빼고 보냄
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record TossSellerAccountUpdateRequest(TossSellerRequest.Account account) {
}
