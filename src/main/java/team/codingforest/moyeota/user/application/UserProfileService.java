package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.domain.Nickname;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.Users;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

/**
 *  공개 프로필(users 테이블) 편집. 개인정보(user_profile)는 여기서 다루지 않는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserProfileService {
    private final Users users;

    @Transactional
    public void update(Long userId, String nickname, String imageUrl) {
        User user = users.findById(userId);

        if(nickname != null) {
            String normalized = new Nickname(nickname).value();
            // 자기 닉네임을 그대로 보낸 요청은 중복이 아니다
            if(!normalized.equals(user.getNickname()) && users.existsByNickname(normalized)) {
                throw new UserException(UserErrorCode.NICKNAME_DUPLICATED);
            }
        }

        user.updateProfile(nickname, imageUrl);
        users.save(user);

        log.info("프로필 수정 userId={}, nicknameChanged={}, imageChanged={}", userId, nickname != null, imageUrl != null);
    }
}
