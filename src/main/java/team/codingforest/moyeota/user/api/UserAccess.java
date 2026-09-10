package team.codingforest.moyeota.user.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserAccess {
    // 토큰이 등록된 유저들만 담김
    Map<Long, String> findFcmTokens(List<Long> userIds);

    Map<Long, MemberSummary> findMemberSummaries(List<Long> userIds);

    /** 표시 이름(닉네임). 유저가 없거나 닉네임 미설정이면 empty */
    Optional<String> findNickname(Long userId);
}
