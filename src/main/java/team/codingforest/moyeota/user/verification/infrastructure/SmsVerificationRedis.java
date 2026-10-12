package team.codingforest.moyeota.user.verification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.verification.domain.SmsVerifications;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SmsVerificationRedis implements SmsVerifications {
    // 키에는 원문 번호 대신 phone_hash 를 씀 - Redis 에 평문 번호가 남지 않게
    private static final String COOLDOWN = "sms:cooldown:";
    private static final String DAILY = "sms:daily:";
    private static final String CODE = "sms:code:";
    private static final String ATTEMPT = "sms:attempt:";
    private static final String VERIFIED = "sms:verified:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryStartCooldown(String phoneHash, Duration cooldown) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(COOLDOWN + phoneHash, "1", cooldown));
    }

    @Override
    public void releaseCooldown(String phoneHash) {
        redisTemplate.delete(COOLDOWN + phoneHash);
    }

    @Override
    public long increaseDailyCount(String phoneHash, Duration window) {
        return increase(DAILY + phoneHash, window);
    }

    @Override
    public void saveCode(String phoneHash, String code, Duration ttl) {
        redisTemplate.delete(ATTEMPT + phoneHash);
        redisTemplate.opsForValue().set(CODE + phoneHash, code, ttl);
    }

    @Override
    public Optional<String> findCode(String phoneHash) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(CODE + phoneHash));
    }

    @Override
    public long increaseAttempts(String phoneHash, Duration ttl) {
        return increase(ATTEMPT + phoneHash, ttl);
    }

    @Override
    public void deleteCode(String phoneHash) {
        redisTemplate.delete(List.of(CODE + phoneHash, ATTEMPT + phoneHash));
    }

    @Override
    public void saveVerified(String token, String phoneHash, Duration ttl) {
        redisTemplate.opsForValue().set(VERIFIED + token, phoneHash, ttl);
    }

    @Override
    public Optional<String> findVerifiedPhone(String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(VERIFIED + token));
    }

    @Override
    public void deleteVerified(String token) {
        redisTemplate.delete(VERIFIED + token);
    }

    // 처음이면 0 + TTL 로 만든 뒤 INCR - INCR 은 TTL 을 유지하므로 TTL 없는 키가 생기지 않음
    private long increase(String key, Duration ttl) {
        redisTemplate.opsForValue().setIfAbsent(key, "0", ttl);
        Long count = redisTemplate.opsForValue().increment(key);
        return count == null ? 0 : count;
    }
}