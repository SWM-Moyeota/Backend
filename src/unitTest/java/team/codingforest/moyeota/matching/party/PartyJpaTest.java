package team.codingforest.moyeota.matching.party;

import team.codingforest.moyeota.matching.api.dto.MatchingTarget;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.domain.RoomType;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Parties 가짜 구현(메모리). 다른 기능 폴더 테스트도 쓰므로 public */
public class PartyJpaTest implements Parties {
    private final Map<Long, Party> store = new HashMap<>();
    private Long sequence = 0L;

    @Override
    public Optional<Party> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Party save(Party party) {
        Long id = party.getId() != null ? party.getId() : ++sequence;
        Party saved = Party.load(id,
                party.getDepartureLocation(), party.getDestinationLocation(),
                party.getDepartureRadius(), party.getDestinationRadius(),
                party.getDeparture(), party.getDestination(), party.getCapacity(),
                party.getMembers(), party.getCreatedAt(), party.getStatus(),
                party.getEstimatedFare(), party.getEstimatedTime(), party.getRoute(), party.getTaxiDriverId(), party.getMatchingStartedAt(), party.getCompletedAt(), party.getVersion(),
                party.getRoomType(), party.getDepartureAt(), party.getReminderSentAt());

        store.put(id, saved);
        return saved;
    }

    @Override
    public List<Party> findRealtimeByStatus(PartyStatus status) {
        return store.values().stream().filter(p -> p.getStatus() == status && p.getRoomType() == RoomType.REALTIME)
                .toList();
    }

    @Override
    public List<PartySummary> findSummariesWithinBounds(PartyStatus status, double swLat, double neLat, double swLng, double neLng, int limit) {
        // 실제 쿼리(between: 양끝 포함, 최신순, 상한)와 같은 규칙 - 출발지 좌표 기준. 메모리에서는 id 가 클수록 최신이다
        return store.values().stream()
                .filter(p -> p.getStatus() == status && p.getRoomType() == RoomType.REALTIME)
                .filter(p -> p.getDepartureLocation().latitude() >= swLat && p.getDepartureLocation().latitude() <= neLat)
                .filter(p -> p.getDepartureLocation().longitude() >= swLng && p.getDepartureLocation().longitude() <= neLng)
                .sorted(Comparator.comparing(Party::getId).reversed())
                .limit(limit)
                .map(p -> new PartySummary(p.getId(), p.getDeparture(), p.getDestination(), (long) p.getMembers().size(),
                        p.getCapacity().value(), p.getStatus(), p.getDepartureLocation().latitude(), p.getDepartureLocation().longitude()))
                .toList();
    }

    @Override
    public boolean existsOngoingByMemberId(Long memberId) {
        return store.values().stream()
                .anyMatch(p -> p.getStatus().isOngoing() && p.hasMember(memberId));
    }

    @Override
    public Optional<Party> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    public Map<Long, Integer> countFinishedRides(List<Long> memberIds) {
        Map<Long, Integer> counts = new HashMap<>();
        for(Party p : store.values()) {
            if(p.getStatus() != PartyStatus.FINISHED) continue;
            for(Long id : memberIds) {
                if(p.hasMember(id)) counts.merge(id, 1, Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public boolean hasOngoingRide(Long driverId) {
        return store.values().stream()
                .anyMatch(p -> (p.getStatus() == PartyStatus.DRIVER_ASSIGNED || p.getStatus() == PartyStatus.IN_RIDE)
                        && driverId.equals(p.getTaxiDriverId()));
    }

    @Override
    public List<MatchingTarget> findMatchingTargets() {
        return store.values().stream()
                .filter(p -> p.getStatus() == PartyStatus.MATCHING)
                .map(p -> new MatchingTarget(p.getId(), p.getMatchingStartedAt()))
                .toList();
    }

    @Override
    public List<Long> findCompletedBefore(Instant before) {
        // 실제 JPQL 과 같은 규칙 - status = COMPLETED 이고 completedAt < before. null 은 비교 대상이 아니다
        return store.values().stream()
                .filter(p -> p.getStatus() == PartyStatus.COMPLETED)
                .filter(p -> p.isReservation()
                        ? p.getDepartureAt().isBefore(before)
                        : p.getCompletedAt() != null && p.getCompletedAt().isBefore(before))
                .map(Party::getId)
                .toList();
    }

    @Override
    public void lockMember(Long memberId) {
    }

    @Override
    public boolean existsOngoingRealtimeByMemberId(Long memberId) {
        return store.values().stream()
                .anyMatch(p -> p.getRoomType() == RoomType.REALTIME && p.getStatus().isOngoing() && p.hasMember(memberId));
    }

    @Override
    public boolean existsOngoingReservationBetween(Long memberId, Instant from, Instant to) {
        return store.values().stream()
                .filter(Party::isReservation)
                .filter(p -> p.getStatus().isOngoing() && p.hasMember(memberId))
                .anyMatch(p -> !p.getDepartureAt().isBefore(from) && !p.getDepartureAt().isAfter(to));
    }

    @Override
    public List<ReservationSummary> findReservations(Instant from, Instant to, int limit) {
        return activeReservations(from, to).limit(limit).toList();
    }

    @Override
    public List<ReservationSummary> findReservationsWithinBounds(Instant from, Instant to, double swLat, double neLat, double swLng, double neLng, int limit) {
        return activeReservations(from, to)
                .filter(s -> s.departureLat() >= swLat && s.departureLat() <= neLat)
                .filter(s -> s.departureLng() >= swLng && s.departureLng() <= neLng)
                .limit(limit)
                .toList();
    }

    private java.util.stream.Stream<ReservationSummary> activeReservations(Instant from, Instant to) {
        return store.values().stream()
                .filter(Party::isReservation)
                .filter(p -> p.getStatus() == PartyStatus.ACTIVE)
                .filter(p -> !p.getDepartureAt().isBefore(from) && !p.getDepartureAt().isAfter(to))
                .sorted(Comparator.comparing(Party::getDepartureAt).thenComparing(Party::getId))
                .map(p -> new ReservationSummary(p.getId(), p.getDeparture(), p.getDestination(), (long) p.getMembers().size(), p.getCapacity().value(), p.getStatus(),
                        p.getDepartureLocation().latitude(), p.getDepartureLocation().longitude(), p.getDestinationLocation().latitude(), p.getDestinationLocation().longitude(), p.getDepartureAt()));
    }

    @Override
    public List<Long> findRecruitingExpired(Instant realtimeCreatedBefore, Instant now) {
        return store.values().stream()
                .filter(p -> p.getStatus() == PartyStatus.ACTIVE)
                .filter(p -> p.isReservation() ? !p.getDepartureAt().isAfter(now) : p.getCreatedAt().isBefore(realtimeCreatedBefore))
                .map(Party::getId)
                .toList();
    }

    @Override
    public List<Long> findReservationsToRemind(Instant from, Instant to) {
        return store.values().stream()
                .filter(Party::isReservation)
                .filter(p -> p.getStatus().isRecruiting() && p.getReminderSentAt() == null)
                .filter(p -> !p.getDepartureAt().isBefore(from) && !p.getDepartureAt().isAfter(to))
                .map(Party::getId)
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return store.containsKey(id);
    }

    @Override
    public boolean hasMember(Long partyId, Long memberId) {
        Party party = store.get(partyId);
        return party != null && party.hasMember(memberId);
    }

    @Override
    public List<NearbyPartySummary> findMatchesWithinRadius(double departLat, double departLng, double destLat, double destLng, int radius) {
        // PostGIS 반경 쿼리는 메모리로 흉내 내지 않는다 - 실제 쿼리 검증은 Postgres 통합 테스트 몫
        return List.of();
    }
}
