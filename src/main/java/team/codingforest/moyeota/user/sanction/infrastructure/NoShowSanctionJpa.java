package team.codingforest.moyeota.user.sanction.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanction;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanctions;
import team.codingforest.moyeota.user.users.infrastructure.UserEntity;
import team.codingforest.moyeota.user.users.infrastructure.UserRepository;

import java.util.Optional;

/**
 *  users 행의 제재 컬럼만 옮긴다. 저장은 더티체킹이라 트랜잭션 안에서 불러야 한다 - UserEntity 가 @DynamicUpdate 라 제재 컬럼만 UPDATE 된다
 */
@Repository
@RequiredArgsConstructor
public class NoShowSanctionJpa implements NoShowSanctions {
    private final UserRepository userRepository;

    @Override
    public Optional<NoShowSanction> findForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId).map(UserEntity::noShowSanction);
    }

    @Override
    public Optional<NoShowSanction> find(Long userId) {
        return userRepository.findById(userId).map(UserEntity::noShowSanction);
    }

    @Override
    public void save(Long userId, NoShowSanction sanction) {
        // findForUpdate 로 이미 읽은 엔티티라 영속성 컨텍스트에서 바로 나온다
        UserEntity entity = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        entity.applyNoShowSanction(sanction);
    }
}
