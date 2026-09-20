package team.codingforest.moyeota.matching.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionTimedOutException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.MatchingAdmission;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@Slf4j
public class RedisMatchingAdmission implements MatchingAdmission {
    private final MatchingTransactions transactions;
    private final ObjectProvider<RedissonClient> clients;

    public RedisMatchingAdmission(MatchingTransactions transactions,
            @Qualifier("matchingRedissonClient") ObjectProvider<RedissonClient> clients) {
        this.transactions = transactions;
        this.clients = clients;
    }

    @Override
    public <T> T execute(Long memberId, Supplier<T> operation) {
        MatchingTransactions.requireNoTransaction();
        RLock lock = acquire(memberId); // DB 트랜잭션과 커넥션을 열기 전에 대기한다.
        try {
            return transactions.execute(operation); // 이 호출이 반환되면 커밋까지 끝난 상태다.
        } catch (PessimisticLockingFailureException | QueryTimeoutException | TransactionTimedOutException e) {
            throw new BusinessException(MatchingErrorCode.MATCHING_BUSY);
        } finally {
            try {
                lock.unlock();
            } catch (RuntimeException e) {
                // 이미 커밋된 성공이나 원래 실패를 해제 오류로 덮어쓰지 않는다.
                // 소유권이 사라졌거나 Redis가 끊긴 경우에도 DB의 사용자 PK가 중복 참여를 방어한다.
                log.warn("매칭 잠금 해제 실패 memberId={} errorType={}", memberId, e.getClass().getSimpleName());
            }
        }
    }

    private RLock acquire(Long memberId) {
        RLock lock;
        boolean acquired;
        try {
            lock = clients.getObject().getLock("moyeota:matching:member:" + memberId);
            // leaseTime을 지정하지 않아 watchdog 갱신을 사용한다. 정합성의 최종 보장은 DB 제약이다.
            acquired = lock.tryLock(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(MatchingErrorCode.MATCHING_BUSY);
        } catch (RuntimeException e) {
            // 잠금 없이 DB 작업을 진행하는 우회는 허용하지 않는다.
            throw new BusinessException(MatchingErrorCode.MATCHING_LOCK_UNAVAILABLE);
        }
        if (!acquired) throw new BusinessException(MatchingErrorCode.MATCHING_BUSY);
        return lock;
    }
}
