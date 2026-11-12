package team.codingforest.moyeota.faresplit.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.faresplit.domain.FareShare;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FareSplitJpa implements FareSplits {
    private static final String UNIQUE_VIOLATION = "23505";

    private final FareSplitJpaRepository repository;
    private final EntityManager em;

    @Override
    public FareSplit create(FareSplit split) {
        FareSplitEntity entity = FareSplitEntity.from(split);

		// match_id 값과 1:1 매핑이기에 save가 아닌 entityManager를 사용
        try {
            em.persist(entity);
            em.flush();
        } catch (PersistenceException e) {
            if(isDuplicateKey(e)) throw new DuplicateKeyException("이미 정산이 있는 방 partyId=" + split.getPartyId(), e);

            throw e;
        }

        return entity.toDomain();
    }

    @Override
    public Optional<FareSplit> findByPartyId(Long partyId) {
        return repository.findWithTransfersByPartyId(partyId)
                .map(FareSplitEntity::toDomain);
    }

    @Override
    public Optional<FareSplit> findByPartyIdForUpdate(Long partyId) {
        return repository.findByPartyIdForUpdate(partyId)
                .map(FareSplitEntity::toDomain);
    }

    @Override
    public void save(FareSplit split) {
        FareSplitEntity entity = repository.findById(split.getPartyId())
                .orElseThrow(() -> new BusinessException(FareSplitErrorCode.FARE_SPLIT_NOT_FOUND));

        entity.update(split);
    }

    @Override
    public List<OpenTransfer> findOpenTransfers(Long memberId) {
        return repository.findOpenTransfersBySender(memberId);
    }

    @Override
    public List<FareShare> findShares(Long memberId, Collection<Long> partyIds) {
        if(partyIds.isEmpty()) return List.of();

        return repository.findShares(memberId, partyIds);
    }

    @Override
    public List<Long> findTransferRequestTargets(Instant now) {
        return repository.findTransferRequestTargetIds(now, FareSplit.MAX_TRANSFER_REQUESTS, FareSplit.REMIND_INTERVAL.toSeconds());
    }

    @Override
    public List<Long> findExpireTargets(Instant now) {
        return repository.findExpireTargetIds(now.minus(FareSplit.EXPIRES_AFTER));
    }

    @Override
    public List<Long> findOpenByPayer(Long payerId) {
        return repository.findOpenIdsByPayer(payerId);
    }

    // 원인을 따라가 중복 키(PK·unique 위반)만 고른다 - FK·NOT NULL·CHECK 위반은 중복이 아니다
    private static boolean isDuplicateKey(Throwable e) {
        for(Throwable cause = e; cause != null; cause = cause.getCause()) {
            if(cause instanceof ConstraintViolationException violation && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) return true;

            if(cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) return true;
        }

        return false;
    }
}
