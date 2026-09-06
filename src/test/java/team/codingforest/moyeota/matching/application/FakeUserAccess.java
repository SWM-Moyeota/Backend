package team.codingforest.moyeota.matching.application;

import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 *  userId → 닉네임만 들고 있는 가짜 UserAccess. 등록 안 된 userId 는 결과에서 빠진다(실제 findAllById 동작과 동일).
 */
class FakeUserAccess implements UserAccess {
    private final Map<Long, MemberSummary> summaries = new HashMap<>();

    void 등록(Long userId, String nickname) {
        summaries.put(userId, new MemberSummary(UUID.randomUUID(), nickname, null, null));
    }

    UUID publicIdOf(Long userId) {
        return summaries.get(userId).publicId();
    }

    @Override
    public Map<Long, String> findFcmTokens(List<Long> userIds) {
        return Map.of();
    }

    @Override
    public Map<Long, MemberSummary> findMemberSummaries(List<Long> userIds) {
        Map<Long, MemberSummary> result = new HashMap<>();
        for(Long id : userIds) {
            if(summaries.containsKey(id)) result.put(id, summaries.get(id));
        }
        return result;
    }
}
