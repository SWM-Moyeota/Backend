package team.codingforest.moyeota.matching.domain;

import java.util.List;
import java.util.Map;

/**
 * 파티 멤버의 실시간 위치 저장소.
 *
 * 기사 위치([team.codingforest.moyeota.dispatch.domain.DriverLocations])와 달리 **반경 검색이 없다** —
 * 읽는 쪽이 늘 "이 방의 이 멤버들"로 정해져 있어 GEO 인덱스가 필요 없다.
 * 오래된 좌표는 남기지 않는다(TTL) — 앱이 꺼진 사람의 낡은 점을 지도에 그리면 거짓 정보가 된다.
 */
public interface MemberLocations {
    void update(Long partyId, Long memberId, double latitude, double longitude);

    /** TTL 안에 보고가 있는 멤버만 담긴다. 없으면 빈 맵 */
    Map<Long, MemberPosition> findAll(Long partyId, List<Long> memberIds);
}
