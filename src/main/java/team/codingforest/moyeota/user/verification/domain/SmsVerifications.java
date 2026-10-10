package team.codingforest.moyeota.user.verification.domain;

import java.time.Duration;
import java.util.Optional;

public interface SmsVerifications {

    // 재전송 대기 시작 - 이미 대기 중이면 false
    boolean tryStartCooldown(String phoneHash, Duration cooldown);

    // 발송이 실패하면 바로 다시 요청할 수 있게 대기를 풂
    void releaseCooldown(String phoneHash);

    // 첫 발송부터 window 동안의 발송 횟수를 1 올리고 올린 값을 돌려줌
    long increaseDailyCount(String phoneHash, Duration window);

    // 새 코드를 저장하고 오입력 횟수를 0으로 되돌림
    void saveCode(String phoneHash, String code, Duration ttl);

    Optional<String> findCode(String phoneHash);

    // 오입력 횟수를 1 올리고 올린 값을 돌려줌
    long increaseAttempts(String phoneHash, Duration ttl);

    // 코드와 오입력 횟수를 함께 지움
    void deleteCode(String phoneHash);

    void saveVerified(String token, String phoneHash, Duration ttl);

    Optional<String> findVerifiedPhone(String token);

    void deleteVerified(String token);
}