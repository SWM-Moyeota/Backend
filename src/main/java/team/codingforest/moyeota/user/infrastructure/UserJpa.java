package team.codingforest.moyeota.user.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.Users;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserJpa implements Users {

    private final UserRepository userRepository;

    @Override
    public User save(User user) {
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
}
