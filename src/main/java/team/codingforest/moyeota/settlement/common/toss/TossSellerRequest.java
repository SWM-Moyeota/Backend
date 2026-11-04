package team.codingforest.moyeota.settlement.common.toss;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 토스 셀러 등록 요청 본문. null 필드는 보내지 않음
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record TossSellerRequest(String refSellerId, String businessType, Company company, Account account) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Company(String name, String representativeName, String businessRegistrationNumber, String email, String phone) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Account(String bankCode, String accountNumber, String holderName) {
    }
}
