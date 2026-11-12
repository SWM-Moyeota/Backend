package team.codingforest.moyeota.faresplit.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

/**
 * 한 사람의 부담 금액
 */
public record FarePayment(Long memberId, int amount) {
	public FarePayment {
		if (amount <= 0) {
			throw new BusinessException(FareSplitErrorCode.INVALID_PAYMENT);
		}
	}
}
