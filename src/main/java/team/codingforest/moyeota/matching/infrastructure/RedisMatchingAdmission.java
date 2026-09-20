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

/**
 *  Redisson RLock 으로 입장을 직렬화한다. 사용자 키, 참가 시에는 방 키까지 사용자 → 방 순서로 잡는다.
 *  잠금 대기는 DB 트랜잭션을 열기 전에 일어나므로 한 방에 몰린 참가 요청이 DB 연결을 동시에 점유하지 않는다.
 *  정합성의 최종 방어는 여전히 DB(현재 참여 PK, 방 행 잠금)다.
 */
@Component
@Slf4j
public class RedisMatchingAdmission implements MatchingAdmission {
    static final String MEMBER_KEY_PREFIX = "moyeota:matching:member:";
    static final String PARTY_KEY_PREFIX = "moyeota:matching:party:";

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
        RLock memberLock = acquire(MEMBER_KEY_PREFIX + memberId); // DB 트랜잭션과 커넥션을 열기 전에 대기한다.
        try {
            return run(operation); // 이 호출이 반환되면 커밋까지 끝난 상태다.
        } finally {
            release(memberLock, "member", memberId);
        }
    }

    @Override
    public <T> T execute(Long memberId, Long partyId, Supplier<T> operation) {
        MatchingTransactions.requireNoTransaction();
        RLock memberLock = acquire(MEMBER_KEY_PREFIX + memberId);
        RLock partyLock;
        try {
            partyLock = acquire(PARTY_KEY_PREFIX + partyId);
        } catch (RuntimeException e) {
            // 방 잠금을 못 잡았으면 사용자 잠금을 바로 돌려준다 - 같은 사용자의 다른 요청이 2초를 더 기다릴 이유가 없다
            release(memberLock, "member", memberId);
            throw e;
        }
        try {
            return run(operation);
        } finally {
            release(partyLock, "party", partyId);    // 잡은 순서의 역순
            release(memberLock, "member", memberId);
        }
    }

    private <T> T run(Supplier<T> operation) {
        try {
            return transactions.execute(operation);
        } catch (PessimisticLockingFailureException | QueryTimeoutException | TransactionTimedOutException e) {
            throw new BusinessException(MatchingErrorCode.MATCHING_BUSY);
        }
    }

    private RLock acquire(String key) {
        RLock lock;
        boolean acquired;
        try {
            lock = clients.getObject().getLock(key);
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

    private void release(RLock lock, String kind, Long id) {
        try {
            lock.unlock();
        } catch (RuntimeException e) {
            // 이미 커밋된 성공이나 원래 실패를 해제 오류로 덮어쓰지 않는다.
            // 소유권이 사라졌거나 Redis가 끊긴 경우에도 DB의 사용자 PK와 방 행 잠금이 정합성을 방어한다.
            log.warn("매칭 잠금 해제 실패 kind={} id={} errorType={}", kind, id, e.getClass().getSimpleName());
        }
    }
}
