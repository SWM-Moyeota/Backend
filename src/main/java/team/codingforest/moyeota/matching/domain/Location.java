package team.codingforest.moyeota.matching.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;

// TODO - 예외처리 해야함
public record Location(double latitude, double longitude) {
    private static final double MIN_LAT = 33.0;
    private static final double MAX_LAT = 39.0;
    private static final double MIN_LNG = 124.0;
    private static final double MAX_LNG = 132.0;

    public Location {
        if(latitude < MIN_LAT || longitude < MIN_LNG || latitude > MAX_LAT || longitude > MAX_LNG) throw new BusinessException(MatchingErrorCode.OUT_OF_SERVICE_AREA);
    }

}
