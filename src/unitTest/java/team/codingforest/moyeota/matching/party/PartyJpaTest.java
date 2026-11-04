package team.codingforest.moyeota.matching.party;

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
                party.getMembers(), party.hostId(), party.getCreatedAt(), party.getStatus(),
                party.getEstimatedFare(), party.getEstimatedTime(), party.getRoute(), party.getTaxiDriverId(), party.getMatchingStartedAt(), party.getCompletedAt(), party.getVersion(),
                party.getRoomType(), party.getDepartureAt(), party.getReminderSentAt(), party.getMeetingNotifiedAt(), party.getDepartureReminderCount());

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
                // 실제 쿼리처럼 노쇼(무효로 돌린 노쇼는 빼고)는 세지 않는다
                boolean rode = p.getMembers().stream().anyMatch(m -> m.getMemberId().equals(id) && !m.isNoShow());
                if(rode) counts.merge(id, 1, Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public List<Long> findAutoCloseTargets(Instant cutoff) {
        // 실제 JPQL 과 같은 규칙 - 열린 방(ACTIVE·COMPLETED) 중 실시간 방은 createdAt < cutoff, 예약 방은 departureAt < cutoff
        return store.values().stream()
                .filter(p -> p.getStatus().isRecruiting())
                .filter(p -> p.isReservation() ? p.getDepartureAt().isBefore(cutoff) : p.getCreatedAt().isBefore(cutoff))
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
    public List<Long> findMeetingNoticeTargets(Instant now) {
        // 실제 JPQL 과 같은 규칙 - 약속이 정해진 열린 방 중 약속 시각이 now 이전이고 알림을 처리하지 않은 방
        return store.values().stream()
                .filter(p -> p.getStatus().isRecruiting() && p.getDepartureAt() != null)
                .filter(p -> !p.getDepartureAt().isAfter(now) && p.getMeetingNotifiedAt() == null)
                .map(Party::getId)
                .toList();
    }

    @Override
    public List<Long> findDepartureReminderTargets(Instant now) {
        // 실제 JPQL 과 같은 규칙 - departure_at + 재촉 간격[처리한 차례 수] 가 now 이전인 열린 방
        return store.values().stream()
                .filter(p -> p.nextDepartureReminderDue().filter(due -> !due.isAfter(now)).isPresent())
                .map(Party::getId)
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
}
