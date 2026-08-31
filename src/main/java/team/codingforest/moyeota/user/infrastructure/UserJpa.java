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
}
