package team.codingforest.moyeota.matching.record;

import team.codingforest.moyeota.matching.record.domain.RideRecords;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.PartyMember;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class FakeRideRecords implements RideRecords {
    private final Map<Long, RideRecord> store = new HashMap<>();
    private final Parties parties;
    public int memberLookups = 0;

    public FakeRideRecords(Parties parties) {
        this.parties = parties;
    }

    @Override
    public Optional<RideRecord> findById(Long matchId) {
        return Optional.ofNullable(store.get(matchId)).map(FakeRideRecords::copy);
    }

    @Override
    public RideRecord save(RideRecord rideRecord) {
        store.put(rideRecord.getMatchId(), copy(rideRecord));
        return copy(rideRecord);
    }

    @Override
    public List<RideRecord> findDroppedOffByMember(Long memberId, int limit) {
        return droppedOffOf(memberId)
                .limit(limit)
                .toList();
    }

    @Override
    public List<RideRecord> findDroppedOffByMemberBefore(Long memberId, Instant dropoffTime, Long matchId, int limit) {
        return droppedOffOf(memberId)
                .filter(h -> h.getDropoffTime().isBefore(dropoffTime)
                        || (h.getDropoffTime().equals(dropoffTime) && h.getMatchId() < matchId))
                .limit(limit)
                .toList();
    }

    @Override
    public Map<Long, List<Long>> findMemberIds(Collection<Long> matchIds) {
        memberLookups++;
        Map<Long, List<Long>> result = new HashMap<>();

        for(Long matchId : matchIds) {
            parties.findById(matchId)
                    .filter(p -> !p.getMembers().isEmpty())
                    .ifPresent(p -> result.put(matchId, p.getMembers().stream()
                            .sorted(Comparator.comparing(PartyMember::getJoinedAt).thenComparing(PartyMember::getMemberId))
                            .map(PartyMember::getMemberId)
                            .toList()));
        }

        return result;
    }

    private Stream<RideRecord> droppedOffOf(Long memberId) {
        return store.values().stream()
                .filter(RideRecord::isDroppedOff)
                .filter(h -> parties.hasMember(h.getMatchId(), memberId))
                .sorted(Comparator.comparing(RideRecord::getDropoffTime).thenComparing(RideRecord::getMatchId).reversed())
                .map(FakeRideRecords::copy);
    }

    private static RideRecord copy(RideRecord h) {
        return RideRecord.load(h.getMatchId(), h.getTaxiDriverId(), h.getDeparturePlace(), h.getDestinationPlace(),
                h.getPickupTime(), h.getDropoffTime(), h.getTotalFare());
    }
}
