package team.codingforest.moyeota.matching.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;

public record Capacity(int value) {
    private static final int MIN_CAPACITY = 1;
    private static final int MAX_CAPACITY = 3;
    public Capacity {
        if(value < MIN_CAPACITY || value > MAX_CAPACITY) throw new BusinessException(MatchingErrorCode.INVALID_CAPACITY);
    }
}
