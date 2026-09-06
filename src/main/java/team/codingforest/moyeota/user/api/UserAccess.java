package team.codingforest.moyeota.user.api;

import java.util.List;
import java.util.Map;

public interface UserAccess {
    // 토큰이 등록된 유저들만 담김
    Map<Long, String> findFcmTokens(List<Long> userIds);
}
