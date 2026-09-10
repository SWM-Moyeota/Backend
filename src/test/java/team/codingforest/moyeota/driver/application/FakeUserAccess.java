package team.codingforest.moyeota.driver.application;

import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** userId → 닉네임만 들고 있는 가짜. 등록 안 된 유저는 empty */
class FakeUserAccess implements UserAccess {
    private final Map<Long, String> nicknames = new HashMap<>();

    void 등록(Long userId, String nickname) { nicknames.put(userId, nickname); }

    @Override
    public Optional<String> findNickname(Long userId) { return Optional.ofNullable(nicknames.get(userId)); }

    @Override
    public Map<Long, String> findFcmTokens(List<Long> userIds) { return Map.of(); }

    @Override
    public Map<Long, MemberSummary> findMemberSummaries(List<Long> userIds) { return Map.of(); }
}
