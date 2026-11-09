package team.codingforest.moyeota.matching.party;

import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.NearbyReservation;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.domain.RoomType;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;

import java.time.Instant;
import java.util.Collection;
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
                party.getDeparture(), party.getDestination(), party.getCapacity(),
                party.getMembers(), party.getHostId(), party.getCreatedAt(), party.getStatus(),
                party.getEstimatedFare(), party.getEstimatedTime(), party.getRoute(), party.getVersion(),
                party.getRoomType(), party.getDepartureAt());

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
        // 실제 쿼리(between: 양끝 포함, 정원이 찬 방 제외, 최신순, 상한)와 같은 규칙 - 출발지 좌표 기준. 메모리에서는 id 가 클수록 최신이다
        return store.values().stream()
                .filter(p -> p.getStatus() == status && p.getRoomType() == RoomType.REALTIME && !p.isFull())
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
                .anyMatch(p -> p.getStatus().isOpen() && p.isMember(memberId));
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
            // 실제 쿼리처럼 - 도착자가 있는 방은 도착한 멤버만, 도착 기록이 없는 옛 방은 멤버 전원을 센다
            List<Long> riders = p.riderIds().isEmpty() ? p.memberIds() : p.riderIds();
            for(Long id : memberIds) {
                if(riders.contains(id)) counts.merge(id, 1, Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public List<Long> findAutoCloseTargets(Instant cutoff) {
        // 실제 JPQL 과 같은 규칙 - 열린 방(ACTIVE·COMPLETED) 중 약속이 있으면 departureAt < cutoff, 없으면 createdAt < cutoff
        return store.values().stream()
                .filter(p -> p.getStatus().isOpen())
                .filter(p -> p.getDepartureAt() != null ? p.getDepartureAt().isBefore(cutoff) : p.getCreatedAt().isBefore(cutoff))
                .map(Party::getId)
                .toList();
    }

    @Override
    public void lockMember(Long memberId) {
    }

    @Override
    public boolean existsOngoingRealtimeByMemberId(Long memberId) {
        return store.values().stream()
                .anyMatch(p -> p.getRoomType() == RoomType.REALTIME && p.getStatus().isOpen() && p.isMember(memberId));
    }

    @Override
    public boolean existsOngoingReservationBetween(Long memberId, Instant from, Instant to) {
        return store.values().stream()
                .filter(Party::isReservation)
                .filter(p -> p.getStatus().isOpen() && p.isMember(memberId))
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

    @Override
    public List<NearbyReservation> findReservationsWithinRadius(Instant from, Instant to, Location departure, Location destination, int radius, int limit) {
        // 반경 판정을 메모리로 흉내 내면 거리 공식을 테스트 쪽에 다시 짜야 하고, 빈 목록으로 답하면 검색 결과가 조용히 틀어진다.
        // 그래서 답하지 않고 바로 실패한다 - 검색 흐름은 서비스 유닛 테스트(mock)와 Postgres 통합 테스트가 본다
        throw new UnsupportedOperationException("예약 방 반경 검색은 PostGIS 몫이라 메모리 가짜로 흉내 내지 않는다");
    }

    /** 실제 쿼리와 같은 규칙 - 정원이 찬 예약 방은 뺀다 */
    private java.util.stream.Stream<ReservationSummary> activeReservations(Instant from, Instant to) {
        return store.values().stream()
                .filter(Party::isReservation)
                .filter(p -> p.getStatus() == PartyStatus.ACTIVE && !p.isFull())
                .filter(p -> !p.getDepartureAt().isBefore(from) && !p.getDepartureAt().isAfter(to))
                .sorted(Comparator.comparing(Party::getDepartureAt).thenComparing(Party::getId))
                .map(p -> new ReservationSummary(p.getId(), p.getDeparture(), p.getDestination(), (long) p.getMembers().size(), p.getCapacity().value(), p.getStatus(),
                        p.getDepartureLocation().latitude(), p.getDepartureLocation().longitude(), p.getDestinationLocation().latitude(), p.getDestinationLocation().longitude(), p.getDepartureAt()));
    }

    @Override
    public List<Long> findRecruitingExpired(Instant realtimeCreatedBefore, Instant now) {
        // 실제 JPQL 과 같은 규칙 - 멤버가 2명 미만인 ACTIVE 방만
        return store.values().stream()
                .filter(p -> p.getStatus() == PartyStatus.ACTIVE && !p.hasCompanion())
                .filter(p -> p.isReservation() ? !p.getDepartureAt().isAfter(now) : p.getCreatedAt().isBefore(realtimeCreatedBefore))
                .map(Party::getId)
                .toList();
    }

    @Override
    public List<Long> findOpenConfirmedIdsDepartingBetween(Instant from, Instant to) {
        // 실제 JPQL 과 같은 규칙 - 열린 확정 방 중 departure_at 이 [from, to)
        return store.values().stream()
                .filter(p -> p.getStatus().isOpen() && p.getDepartureAt() != null)
                .filter(p -> !p.getDepartureAt().isBefore(from) && p.getDepartureAt().isBefore(to))
                .map(Party::getId)
                .toList();
    }

    @Override
    public List<Long> findOpenReservationIdsDepartingBetween(Instant from, Instant to) {
        return findOpenConfirmedIdsDepartingBetween(from, to).stream()
                .filter(id -> store.get(id).isReservation())
                .toList();
    }

    @Override
    public List<NearbyPartySummary> findMatchesWithinRadius(double departLat, double departLng, double destLat, double destLng, int radius) {
        // PostGIS 반경 쿼리는 메모리로 흉내 내지 않는다 - 실제 쿼리 검증은 Postgres 통합 테스트 몫
        return List.of();
    }

    @Override
    public boolean isNearDeparture(Long partyId, double latitude, double longitude, double radiusMeters) {
        // 거리 공식을 여기서 다시 짜면 걷어낸 하버사인이 테스트 쪽에 되살아나고, 늘 true/false 로 답하면 도착 결과가 조용히 틀어진다.
        // 그래서 답하지 않고 바로 실패한다 - 도착 흐름은 서비스 유닛 테스트(mock)와 Postgres 통합 테스트가 본다
        throw new UnsupportedOperationException("출발지 반경 판정은 PostGIS 몫이라 메모리 가짜로 흉내 내지 않는다");
    }

    @Override
    public Map<Long, PartyPlaces> findPlaces(Collection<Long> ids) {
        Map<Long, PartyPlaces> places = new HashMap<>();
        for(Long id : ids) {
            Party party = store.get(id);
            if(party != null) places.put(id, new PartyPlaces(party.getDeparture(), party.getDestination()));
        }

        return places;
    }
}
