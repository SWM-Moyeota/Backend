package team.codingforest.moyeota.matching.faresplit.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartyRiders;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplit;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplits;
import team.codingforest.moyeota.matching.faresplit.domain.OpenTransfer;
import team.codingforest.moyeota.matching.faresplit.domain.RideFare;
import team.codingforest.moyeota.matching.faresplit.domain.TransferRequestSchedule;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FareSplitJpa implements FareSplits {
    private final FareSplitJpaRepository repository;
    private final EntityManager em;
    private final PartyAccess partyAccess;

    @Override
    public FareSplit create(FareSplit split) {
        FareSplitEntity entity = FareSplitEntity.from(split);

        // 키(방 번호)를 직접 넣는 엔티티라 save 는 merge 로 돌아 같은 방의 행을 덮어쓸 수 있다.
        // persist 로 INSERT 만 하고 바로 flush 해 PK 중복을 이 자리에서 드러낸다
        em.persist(entity);
        em.flush();

        return entity.toDomain(split.senderIds());
    }

    @Override
    public Optional<FareSplit> findByPartyId(Long partyId) {
        return repository.findWithTransfersByPartyId(partyId)
                .map(entity -> entity.toDomain(joinOrder(partyId)));
    }

    // 송금(지연 로딩)은 잠근 뒤에 읽으므로 앞서 커밋된 변경을 본다
    @Override
    public Optional<FareSplit> findByPartyIdForUpdate(Long partyId) {
        return repository.findByPartyIdForUpdate(partyId)
                .map(entity -> entity.toDomain(joinOrder(partyId)));
    }

    @Override
    public boolean existsByPartyId(Long partyId) {
        return repository.existsById(partyId);
    }

    @Override
    public void save(FareSplit split) {
        FareSplitEntity entity = repository.findById(split.getPartyId())
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.FARE_SPLIT_NOT_FOUND));

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
    public List<RideFare> findRideFares(Long memberId, Collection<Long> partyIds) {
        if(partyIds.isEmpty()) return List.of();

        return repository.findRideFares(memberId, partyIds);
    }

    @Override
    public List<Long> findTransferRequestTargets(Instant now) {
        return repository.findTransferRequestTargetIds(TransferRequestSchedule.startedBefore(2, now),
                TransferRequestSchedule.startedBefore(3, now), TransferRequestSchedule.startedBefore(4, now));
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

    // 송금을 방 합류 순으로 담는다 - 합류 순은 matching 이 정한다(방 상세 멤버 정렬과 같은 기준)
    private List<Long> joinOrder(Long partyId) {
        return partyAccess.findRiders(partyId).map(PartyRiders::memberIds).orElse(List.of());
    }
}
