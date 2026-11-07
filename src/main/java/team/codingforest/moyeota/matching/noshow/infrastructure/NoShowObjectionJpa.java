package team.codingforest.moyeota.matching.noshow.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.noshow.domain.NoShowHistory;
import team.codingforest.moyeota.matching.noshow.domain.NoShowObjection;
import team.codingforest.moyeota.matching.noshow.domain.NoShowObjections;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionStatus;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionSummary;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NoShowObjectionJpa implements NoShowObjections {
    private final NoShowObjectionJpaRepository repository;

    @Override
    public NoShowObjection save(NoShowObjection objection) {
        if(objection.getId() == null) {
            // 유니크 제약 위반을 이 자리에서 드러내려고 바로 flush 한다
            return repository.saveAndFlush(NoShowObjectionEntity.from(objection)).toDomain();
        }

        NoShowObjectionEntity entity = repository.findById(objection.getId())
                .orElseThrow(() -> new BusinessException(MatchingErrorCode.OBJECTION_NOT_FOUND));
        entity.update(objection);   // 더티체킹

        return entity.toDomain();
    }

    @Override
    public Optional<NoShowObjection> findByIdForUpdate(Long id) {
        return repository.findByIdForUpdate(id).map(NoShowObjectionEntity::toDomain);
    }

    @Override
    public boolean exists(Long userId, Long partyId) {
        return repository.existsByUserIdAndPartyId(userId, partyId);
    }

    @Override
    public List<ObjectionSummary> findByStatus(ObjectionStatus status, int limit) {
        return repository.findSummariesByStatus(status, Limit.of(limit));
    }

    @Override
    public List<NoShowHistory> findNoShowHistory(Long userId, int limit) {
        return repository.findNoShowHistory(userId, Limit.of(limit));
    }
}
