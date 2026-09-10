package team.codingforest.moyeota.user.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.Users;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

import java.util.*;

@Repository
@RequiredArgsConstructor
public class UserJpa implements Users {

    private final UserRepository userRepository;

    @Override
    public User save(User user) {
        // 기존 ID가 있는 경우 update
        if(user.getId() != null) {
            UserEntity entity = userRepository.findById(user.getId())
                    .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

            entity.update(user);
            return userRepository.save(entity).toDomain();
        }

        return userRepository.save(UserEntity.from(user)).toDomain();
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id).map(UserEntity::toDomain).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    @Override
    public Optional<User> findByPublicId(UUID publicId) {
        return userRepository.findByPublicId(publicId).map(UserEntity::toDomain);
    }

    @Override
    public Map<Long, String> findFcmTokens(List<Long> userIds) {
        if(userIds.isEmpty()) return Map.of();

        Map<Long, String> tokens = new HashMap<>();

        for(UserEntity entity : userRepository.findAllByIdInAndFcmTokenIsNotNull(userIds)) {
            tokens.put(entity.getId(), entity.getFcmToken());
        }

        return tokens;
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    @Override
    public List<User> findAllByIds(List<Long> ids) {
        if(ids.isEmpty()) return List.of();
        return userRepository.findAllById(ids).stream().map(UserEntity::toDomain).toList();
    }
}
