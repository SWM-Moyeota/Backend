package team.codingforest.moyeota.user.local.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.local.domain.LocalUser;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

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
                .map(LocalUserEntity::toDomain).orElseThrow(() -> new UserException(UserErrorCode.LOGIN_FAILED));
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return localUserRepository.existsByLoginId(loginId);
    }
}
