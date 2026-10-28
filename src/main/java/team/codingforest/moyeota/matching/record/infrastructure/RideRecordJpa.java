package team.codingforest.moyeota.matching.record.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.matching.record.domain.RideRecords;
import team.codingforest.moyeota.matching.record.domain.RideRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RideRecordJpa implements RideRecords {

    private final RideRecordJpaRepository rideRecordJpaRepository;

    @Override
    public Optional<RideRecord> findById(Long matchId) {
        return rideRecordJpaRepository.findById(matchId)
                .map(RideRecordEntity::toDomain);
    }

    @Override
    public RideRecord save(RideRecord rideRecord) {
        return rideRecordJpaRepository.findById(rideRecord.getMatchId())
                .map(entity -> entity.update(rideRecord))
                .orElseGet(() -> rideRecordJpaRepository.save(RideRecordEntity.from(rideRecord)))
                .toDomain();
    }

    @Override
    public List<RideRecord> findDroppedOffByMember(Long memberId, int limit) {
        return rideRecordJpaRepository.findDroppedOffByMember(memberId, Limit.of(limit)).stream()
                .map(RideRecordEntity::toDomain).toList();
    }

    @Override
    public List<RideRecord> findDroppedOffByMemberBefore(Long memberId, Instant dropoffTime, Long matchId, int limit) {
        return rideRecordJpaRepository.findDroppedOffByMemberBefore(memberId, dropoffTime, matchId, Limit.of(limit)).stream()
                .map(RideRecordEntity::toDomain).toList();
    }

    @Override
    public Map<Long, List<Long>> findMemberIds(Collection<Long> matchIds) {
        if(matchIds.isEmpty()) return Map.of();

        Map<Long, List<Long>> memberIds = new HashMap<>();

        for(MatchMemberRow row : rideRecordJpaRepository.findMembersByMatchIds(matchIds)) {
            memberIds.computeIfAbsent(row.getMatchId(), id -> new ArrayList<>()).add(row.getMemberId());
        }

        return memberIds;
    }
}
