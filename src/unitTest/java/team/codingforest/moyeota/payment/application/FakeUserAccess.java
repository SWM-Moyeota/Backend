package team.codingforest.moyeota.payment.application;

import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** userId → publicId 만 들고 있는 가짜 */
class FakeUserAccess implements UserAccess {
    private final Map<Long, UUID> publicIds = new HashMap<>();

    UUID 등록(Long userId) {
        UUID id = UUID.randomUUID();
        publicIds.put(userId, id);
        return id;
    }

    @Override
    public Map<Long, String> findFcmTokens(List<Long> userIds) { return Map.of(); }

    @Override
    public Map<Long, MemberSummary> findMemberSummaries(List<Long> userIds) {
        Map<Long, MemberSummary> result = new HashMap<>();
        for(Long id : userIds) {
            if(publicIds.containsKey(id)) result.put(id, new MemberSummary(publicIds.get(id), "닉" + id, null, null));
        }
        return result;
    }

    @Override
    public Optional<String> findNickname(Long userId) { return Optional.empty(); }
}
