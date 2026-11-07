package team.codingforest.moyeota.matching.api;

import team.codingforest.moyeota.matching.api.dto.RideFare;

import java.util.Collection;
import java.util.List;

/**
 *  탑승 기록에 채울 택시비와 내 몫. 택시비 정산 쪽이 구현한다 - matching 이 정산 코드를 보지 않게 의존을 뒤집어 둔다
 */
public interface RideFares {
    /** partyIds 중 정산이 있고 memberId 가 받는 사람이거나 보내는 사람인 방만 준다(정산 상태 무관). 한 번에 읽는다 */
    List<RideFare> find(Long memberId, Collection<Long> partyIds);
}
