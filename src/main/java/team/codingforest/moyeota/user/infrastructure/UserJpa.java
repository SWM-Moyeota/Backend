package team.codingforest.moyeota.user.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.Users;

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
        return userRepository.findById(id).map(UserEntity::toDomain).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
    }
}
