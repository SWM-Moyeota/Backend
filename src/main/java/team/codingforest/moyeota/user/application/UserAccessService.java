package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.api.UserAccess;
import team.codingforest.moyeota.user.domain.Users;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
class UserAccessService implements UserAccess {
    private final Users users;

    @Transactional(readOnly = true)
    @Override
    public Map<Long, String> findFcmTokens(List<Long> userIds) {
        return users.findFcmTokens(userIds);
    }
}
