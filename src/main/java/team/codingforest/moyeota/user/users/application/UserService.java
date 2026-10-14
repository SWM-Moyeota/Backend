package team.codingforest.moyeota.user.users.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.users.domain.Nickname;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;

/**
 *  공개 프로필(users 테이블) 편집. 개인정보(user_profile)는 여기서 다루지 않는다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final Users users;
    private final ProfileImages profileImages;

    /**
     * 닉네임과 이미지 중 넘어온 것만 바꿈
     *
     * @param userId 바꿀 사용자
     * @param nickname 새 닉네임. null 이면 유지
     * @param imageKey 제공 이미지 키. null 이면 유지
     */
    @Transactional
    public void update(Long userId, String nickname, String imageKey) {
        // 목록 밖의 키(외부 URL 등)가 다른 사람 화면에 그려지지 않도록 저장 전에 막음
        if(imageKey != null && !profileImages.isPreset(imageKey)) {
            throw new UserException(UserErrorCode.INVALID_PROFILE_IMAGE);
        }

        User user = users.findById(userId);

        if(nickname != null) {
            String normalized = new Nickname(nickname).value();
            // 자기 닉네임을 그대로 보낸 요청은 중복이 아니다
            if(!normalized.equals(user.getNickname()) && users.existsByNickname(normalized)) {
                throw new UserException(UserErrorCode.NICKNAME_DUPLICATED);
            }
        }

        user.updateProfile(nickname, imageKey);
        users.save(user);

        log.info("프로필 수정 userId={}, nicknameChanged={}, imageChanged={}", userId, nickname != null, imageKey != null);
    }

    /**
     * 프로필 이미지를 기본 이미지로 되돌림. 이미 기본이어도 성공
     *
     * @param userId 되돌릴 사용자
     */
    @Transactional
    public void resetImage(Long userId) {
        User user = users.findById(userId);
        user.resetImage();
        users.save(user);

        log.info("프로필 이미지 초기화 userId={}", userId);
    }
}
