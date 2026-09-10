package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.Users;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class UserAccessService implements UserAccess {
    private final Users users;

    @Transactional(readOnly = true)
    @Override
    public Map<Long, String> findFcmTokens(List<Long> userIds) {
        return users.findFcmTokens(userIds);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<Long, MemberSummary> findMemberSummaries(List<Long> userIds) {
        Map<Long, MemberSummary> result = new HashMap<>();

        for(User user : users.findAllByIds(userIds)) {
            result.put(user.getId(), new MemberSummary(user.getPublicId(), user.getNickname(), user.getImageUrl(), user.getBadgeId()));
        }

        return result;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<String> findNickname(Long userId) {
        // findById 는 없으면 예외를 던지므로 목록 조회로 empty 를 만든다
        return users.findAllByIds(List.of(userId)).stream()
                .findFirst()
                .map(User::getNickname);
    }
}
