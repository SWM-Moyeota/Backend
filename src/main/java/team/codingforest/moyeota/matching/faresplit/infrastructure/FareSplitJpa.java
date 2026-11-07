package team.codingforest.moyeota.matching.faresplit.infrastructure;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplit;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplits;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FareSplitJpa implements FareSplits {
    private final FareSplitJpaRepository repository;
    private final EntityManager em;

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
                .map(entity -> entity.toDomain(repository.findMemberIdsInJoinOrder(partyId)));
    }

    @Override
    public boolean existsByPartyId(Long partyId) {
        return repository.existsById(partyId);
    }
}
