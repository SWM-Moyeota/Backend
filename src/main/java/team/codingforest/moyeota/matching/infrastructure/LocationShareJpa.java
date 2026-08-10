package team.codingforest.moyeota.matching.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.matching.domain.LocationShare;
import team.codingforest.moyeota.matching.domain.LocationShares;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LocationShareJpa implements LocationShares {
    private final LocationShareJpaRepository delegate;

    @Override
    public LocationShare save(LocationShare share) {
        if(share.getId() == null) {
            return delegate.save(LocationShareEntity.from(share)).toDomain();
        }
        LocationShareEntity entity = delegate.findById(share.getId())
                .orElseThrow(() -> new IllegalStateException("수정 대상이 없음 : " + share.getId()));

        entity.update(share);;
        return entity.toDomain();
    }

    @Override
    public Optional<LocationShare> findOngoing(Long partyId, Long memberId) {
        return delegate.findOngoing(partyId, memberId)
                .map(LocationShareEntity::toDomain);
    }

    @Override
    public List<LocationShare> findAllOngoingByPartyId(Long partyId) {
        return delegate.findAllOngoing(partyId).stream().map(LocationShareEntity::toDomain).toList();
    }
}
