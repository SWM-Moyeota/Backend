package team.codingforest.moyeota.matching.record;

import team.codingforest.moyeota.matching.record.domain.RideRecords;
import team.codingforest.moyeota.matching.record.domain.RideRecord;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
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
    public List<RideRecord> findByMember(Long memberId, int limit) {
        return ridesOf(memberId)
                .limit(limit)
                .toList();
    }

    @Override
    public List<RideRecord> findByMemberBefore(Long memberId, Instant pickupTime, Long matchId, int limit) {
        return ridesOf(memberId)
                .filter(h -> h.getPickupTime().isBefore(pickupTime)
                        || (h.getPickupTime().equals(pickupTime) && h.getMatchId() < matchId))
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
                    .ifPresent(p -> result.put(matchId, riders(p).stream()
                            .sorted(Comparator.comparing(PartyMember::getJoinedAt).thenComparing(PartyMember::getMemberId))
                            .map(PartyMember::getMemberId)
                            .toList()));
        }

        return result;
    }

    private Stream<RideRecord> ridesOf(Long memberId) {
        return store.values().stream()
                .filter(h -> rode(h.getMatchId(), memberId))
                .sorted(Comparator.comparing(RideRecord::getPickupTime).thenComparing(RideRecord::getMatchId).reversed())
                .map(FakeRideRecords::copy);
    }

    /** 실제 쿼리처럼 방 멤버에게만 기록이 있다 */
    private boolean rode(Long matchId, Long memberId) {
        return parties.findById(matchId)
                .map(p -> riders(p).stream().anyMatch(m -> m.getMemberId().equals(memberId)))
                .orElse(false);
    }

    /** 실제 쿼리와 같은 규칙 - 도착한 멤버만 탄 사람이다 */
    private static List<PartyMember> riders(Party p) {
        return p.getMembers().stream().filter(PartyMember::isArrived).toList();
    }

    private static RideRecord copy(RideRecord h) {
        return RideRecord.load(h.getMatchId(), h.getDeparturePlace(), h.getDestinationPlace(), h.getPickupTime());
    }
}
