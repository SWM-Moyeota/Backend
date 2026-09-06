package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.Users;

/**
 *  승객 앱 FCM 토큰 등록/삭제.
 *  로그아웃 API 는 refresh 토큰만 받으므로 토큰 삭제는 프론트가 로그아웃 직전에 따로 호출한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserFcmTokenService {
    private final Users users;

    @Transactional
    public void register(Long userId, String token) {
        User user = users.findById(userId);

        user.registerFcmToken(token);
        users.save(user);

        log.info("승객 FCM 토큰 등록 userId={}", userId);
    }

    @Transactional
    public void remove(Long userId) {
        User user = users.findById(userId);

        user.clearFcmToken();
        users.save(user);

        log.info("승객 FCM 토큰 삭제 userId={}", userId);
    }
}
