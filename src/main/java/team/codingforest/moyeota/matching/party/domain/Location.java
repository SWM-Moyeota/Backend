package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.geo.ServiceArea;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

public record Location(double latitude, double longitude) {
	public Location {
		if(!ServiceArea.contains(latitude, longitude)) throw new BusinessException(MatchingErrorCode.OUT_OF_SERVICE_AREA);
	}
}
