package team.codingforest.moyeota.user.users.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class UserAccessService implements UserAccess {
    private final Users users;
    private final ProfileImages profileImages;

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
            // 탈퇴자는 배지도 숨긴다 - 이름만 바꾸고 배지가 남으면 누군지 짐작할 단서가 된다
            Long badgeId = user.isDeleted() ? null : user.getBadgeId();
            result.put(user.getId(), new MemberSummary(user.getPublicId(), user.displayNickname(), profileImages.toUrl(user.getImageUrl()), badgeId));
        }

        return result;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<String> findNickname(Long userId) {
        // findById 는 없으면 예외를 던지므로 목록 조회로 empty 를 만든다
        return users.findAllByIds(List.of(userId)).stream()
                .findFirst()
                .map(User::displayNickname);
    }
}
