package team.codingforest.moyeota.user.application;

import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.Users;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 *  인메모리 Users. 실제 JPA 처럼 저장→복원(restore) 왕복을 거쳐 필드 유실을 잡는다.
 */
class FakeUsers implements Users {
    private final Map<Long, User> store = new HashMap<>();
    private long seq = 0;

    @Override
    public User save(User user) {
        Long id = user.getId() != null ? user.getId() : ++seq;
        User restored = User.restore(id, user.getPublicId(), user.getNickname(), user.getImageUrl(),
                user.getLoginType(), user.getBadgeId(), user.getFcmToken(), user.getUpdatedAt());
        store.put(id, restored);
        return restored;
    }

    @Override
    public User findById(Long id) {
        User user = store.get(id);
        if(user == null) throw new UserException(UserErrorCode.USER_NOT_FOUND);
        return user;
    }

    @Override
    public Optional<User> findByPublicId(UUID publicId) {
        return store.values().stream().filter(u -> u.getPublicId().equals(publicId)).findFirst();
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return store.values().stream().anyMatch(u -> nickname.equals(u.getNickname()));
    }

    @Override
    public List<User> findAllByIds(List<Long> ids) {
        return ids.stream().map(store::get).filter(u -> u != null).toList();
    }

    @Override
    public Map<Long, String> findFcmTokens(List<Long> userIds) {
        Map<Long, String> tokens = new HashMap<>();
        for(Long id : userIds) {
            User user = store.get(id);
            if(user != null && user.hasFcmToken()) tokens.put(id, user.getFcmToken());
        }
        return tokens;
    }
}
