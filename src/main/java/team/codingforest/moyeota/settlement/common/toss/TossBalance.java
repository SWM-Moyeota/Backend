package team.codingforest.moyeota.settlement.common.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 토스 지급대행 잔액. 테스트 환경은 availableAmount 만 줌
 *
 * @param pendingAmount   아직 지급할 수 없는 잔액
 * @param availableAmount 지급할 수 있는 잔액
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossBalance(Amount pendingAmount, Amount availableAmount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(String currency, BigDecimal value) {
    }
}
