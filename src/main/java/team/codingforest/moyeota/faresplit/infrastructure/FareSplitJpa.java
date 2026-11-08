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
import team.codingforest.moyeota.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.faresplit.domain.TransferRequestSchedule;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FareSplitJpa implements FareSplits {
    // unique_violation - Postgres 와 H2 가 같은 값을 쓴다
    private static final String UNIQUE_VIOLATION = "23505";

    private final FareSplitJpaRepository repository;
    private final EntityManager em;

    @Override
    public FareSplit create(FareSplit split) {
        FareSplitEntity entity = FareSplitEntity.from(split);

        // 키(방 번호)를 직접 넣는 엔티티라 save 는 merge 로 돌아 같은 방의 행을 덮어쓸 수 있다.
        // persist 로 INSERT 만 하고 바로 flush 해 PK 중복을 이 자리에서 드러낸다
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

    // 송금(지연 로딩)은 잠근 뒤에 읽으므로 앞서 커밋된 변경을 본다
    @Override
    public Optional<FareSplit> findByPartyIdForUpdate(Long partyId) {
        return repository.findByPartyIdForUpdate(partyId)
                .map(FareSplitEntity::toDomain);
    }

    @Override
    public boolean existsByPartyId(Long partyId) {
        return repository.existsById(partyId);
    }

    @Override
    public void save(FareSplit split) {
        FareSplitEntity entity = repository.findById(split.getPartyId())
                .orElseThrow(() -> new BusinessException(FareSplitErrorCode.FARE_SPLIT_NOT_FOUND));

        entity.update(split);   // 더티체킹
    }

    // 송금 행은 cascade 로 정산보다 먼저 지워진다
    @Override
    public void delete(Long partyId) {
        repository.findById(partyId).ifPresent(repository::delete);
    }

    @Override
    public List<OpenTransfer> findOpenTransfers(Long memberId) {
        return repository.findTransfersBySender(memberId, FareSplitStatus.OPEN);
    }

    @Override
    public List<FareShare> findShares(Long memberId, Collection<Long> partyIds) {
        if(partyIds.isEmpty()) return List.of();

        return repository.findShares(memberId, partyIds);
    }

    @Override
    public List<Long> findTransferRequestTargets(Instant now) {
        return repository.findTransferRequestTargetIds(TransferRequestSchedule.startedBefore(2, now), TransferRequestSchedule.startedBefore(3, now));
    }

    @Override
    public List<Long> findConfirmRequestTargets(Instant now) {
        return repository.findConfirmRequestTargetIds(now.minus(FareSplit.CONFIRM_REQUEST_AFTER));
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
