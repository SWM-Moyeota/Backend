package team.codingforest.moyeota.matching.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionTimedOutException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.MatchingAdmission;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;
import team.codingforest.moyeota.user.api.MatchingMemberLock;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class DbMatchingAdmission implements MatchingAdmission {
    private final MatchingTransactions transactions;
    private final MatchingMemberLock memberLock;

    @Override
    public <T> T execute(Long memberId, Supplier<T> operation) {
        try {
            return transactions.execute(() -> {
                // 모든 입장 경로가 사용자 → 방 순서를 따른다. DB 커밋/롤백 시 두 잠금이 해제된다.
                memberLock.acquire(memberId);
                return operation.get();
            });
        } catch (PessimisticLockingFailureException | QueryTimeoutException | TransactionTimedOutException e) {
            throw new BusinessException(MatchingErrorCode.MATCHING_BUSY);
        }
    }
}
