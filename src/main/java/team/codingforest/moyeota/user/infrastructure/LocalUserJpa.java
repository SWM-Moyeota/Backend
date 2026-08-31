package team.codingforest.moyeota.user.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.domain.LocalUser;
import team.codingforest.moyeota.user.domain.LocalUsers;
import team.codingforest.moyeota.user.domain.PasswordHasher;

@Repository
@RequiredArgsConstructor
public class LocalUserJpa implements LocalUsers {
    private final LocalUserRepository localUserRepository;
    private final PasswordHasher passwordHasher;

    @Override
    public void register(Long userId, String loginId, String password) {
        localUserRepository.save(LocalUserEntity.from(userId, loginId, passwordHasher.hash(password)));
    }

    @Override
    public LocalUser findByLoginId(String loginId) {
        return localUserRepository.findByLoginId(loginId)
                .map(LocalUserEntity::toDomain).orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return localUserRepository.existsByLoginId(loginId);
    }

    @Override
    public boolean validatePassword(String loginId, String rawPassword) {
        LocalUser localUser = findByLoginId(loginId);

        return passwordHasher.matches(rawPassword, localUser.getPassword());
    }
}
