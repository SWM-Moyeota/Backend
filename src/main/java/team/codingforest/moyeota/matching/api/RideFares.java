package team.codingforest.moyeota.matching.api;

import team.codingforest.moyeota.matching.api.dto.RideFare;

import java.util.Collection;
import java.util.List;

/**
 *  정산에서 사용하는 공개 API
 */
public interface RideFares {
    /** partyIds 중 정산이 있고 memberId 가 받는 사람이거나 보내는 사람인 방만 준다(정산 상태 무관). 한 번에 읽는다 */
    List<RideFare> find(Long memberId, Collection<Long> partyIds);
}
