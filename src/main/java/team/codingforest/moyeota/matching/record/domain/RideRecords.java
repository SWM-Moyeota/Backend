package team.codingforest.moyeota.matching.record.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RideRecords {
    Optional<RideRecord> findById(Long matchId);
    RideRecord save(RideRecord rideRecord);
    List<RideRecord> findByMember(Long memberId, int limit);
    List<RideRecord> findByMemberBefore(Long memberId, Instant pickupTime, Long matchId, int limit);
    Map<Long, List<Long>> findMemberIds(Collection<Long> matchIds);
}
