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

    @Override
    public void register(Long userId, String loginId, String hashedPassword) {
        localUserRepository.save(LocalUserEntity.from(userId, loginId, hashedPassword));
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
}
