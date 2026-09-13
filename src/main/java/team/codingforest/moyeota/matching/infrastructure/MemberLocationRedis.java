package team.codingforest.moyeota.matching.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.matching.domain.MemberLocations;
import team.codingforest.moyeota.matching.domain.MemberPosition;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 파티 멤버 위치를 Redis 문자열 키에 담는다 — `party:{partyId}:member:{memberId}` → `"위도,경도"`.
 *
 * GEO 대신 단순 키를 쓰는 이유는 반경 검색이 필요 없어서다(포트 설명 참고). 멤버는 방당 2~4명이라
 * 다건 조회도 `multiGet` 한 번이면 끝난다.
 *
 * [#TTL] 보고가 끊기면 값이 사라진다 — 앱을 끈 사람의 낡은 점을 계속 그리지 않기 위해서다.
 * 보고 주기(앱 5초)보다 넉넉히 길게 잡아 잠깐의 네트워크 끊김으로 점이 깜빡이지 않게 한다.
 */
@Repository
@RequiredArgsConstructor
public class MemberLocationRedis implements MemberLocations {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void update(Long partyId, Long memberId, double latitude, double longitude) {
        redisTemplate.opsForValue().set(key(partyId, memberId), latitude + "," + longitude, TTL);
    }

    @Override
    public Map<Long, MemberPosition> findAll(Long partyId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return Map.of();

        List<String> keys = memberIds.stream().map(id -> key(partyId, id)).toList();
        List<String> values = redisTemplate.opsForValue().multiGet(keys);
        if (values == null) return Map.of();

        Map<Long, MemberPosition> positions = new HashMap<>();
        for (int i = 0; i < memberIds.size(); i++) {
            MemberPosition position = parse(values.get(i));
            if (position != null) positions.put(memberIds.get(i), position);
        }
        return positions;
    }

    /** 형식이 깨진 값은 없는 것으로 친다 — 지도에 엉뚱한 점을 찍느니 안 그리는 편이 낫다 */
    private MemberPosition parse(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(",");
        if (parts.length != 2) return null;
        try {
            return new MemberPosition(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String key(Long partyId, Long memberId) {
        return "party:" + partyId + ":member:" + memberId;
    }
}
