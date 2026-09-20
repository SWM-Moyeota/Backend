package team.codingforest.moyeota.matching.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.TransactionTimedOutException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.MatchingAdmission;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 비교 실험 전용. 고정 TTL이며 갱신과 재진입을 지원하지 않는다. 운영 빈으로 등록하지 않는다. */
@Slf4j
class LettuceMatchingAdmission implements MatchingAdmission {
    static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final MatchingTransactions transactions;
    private final StringRedisTemplate redis;
    LettuceMatchingAdmission(MatchingTransactions transactions, StringRedisTemplate redis) {
        this.transactions = transactions;
        this.redis = redis;
    }
    @Override
    public <T> T execute(Long memberId, Supplier<T> operation) {
        MatchingTransactions.requireNoTransaction();
        String key = "moyeota:matching:lettuce:member:" + memberId;
        String token = UUID.randomUUID().toString();
        acquire(key, token);
        try {
            return transactions.execute(operation);
        } catch (PessimisticLockingFailureException | QueryTimeoutException | TransactionTimedOutException e) {
            throw new BusinessException(MatchingErrorCode.MATCHING_BUSY);
        } finally {
            try { redis.execute(RELEASE, List.of(key), token); }
            catch (RuntimeException e) {
                log.warn("실험용 잠금 해제 실패 memberId={} errorType={}", memberId, e.getClass().getSimpleName());
            }
        }
    }
    private void acquire(String key, String token) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        int attempts = 0;
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted()) throw new BusinessException(MatchingErrorCode.MATCHING_BUSY);
            boolean acquired;
            try { acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(30))); }
            catch (RuntimeException e) { throw new BusinessException(MatchingErrorCode.MATCHING_LOCK_UNAVAILABLE); }
            if (acquired) return;
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            long backoff = TimeUnit.MILLISECONDS.toNanos(ThreadLocalRandom.current().nextLong(5, Math.min(50, 10 + attempts++ * 5) + 1));
            try { TimeUnit.NANOSECONDS.sleep(Math.min(remaining, backoff)); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(MatchingErrorCode.MATCHING_BUSY);
            }
        }
        throw new BusinessException(MatchingErrorCode.MATCHING_BUSY);
    }
}
