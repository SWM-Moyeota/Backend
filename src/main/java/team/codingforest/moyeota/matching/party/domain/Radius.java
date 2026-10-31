package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

public record Radius(int meters) {
    private static final int MIN_RAD = 100;
    private static final int MAX_RAD = 500;
    public static final Radius DEFAULT = new Radius(500);

    public Radius {
        if(meters < MIN_RAD || meters > MAX_RAD) throw new BusinessException(MatchingErrorCode.INVALID_RADIUS);
    }
}