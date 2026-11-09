package team.codingforest.moyeota.matching.party.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.NearbyReservation;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PartyJpa implements Parties {

    private final PartyJpaRepository partyJpaRepository;

    @Override
    public Optional<Party> findById(Long id) {
        return partyJpaRepository.findWithMembersById(id)
                .map(PartyEntity::toDomain);
    }

    @Override
    public Party save(Party party) {
        PartyEntity entity;

        if(party.getId() == null) {
            entity = PartyEntity.from(party);
            partyJpaRepository.save(entity);
        }
        else {
            entity = partyJpaRepository.findById(party.getId())
                    .orElseThrow(() -> new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND));
            entity.update(party);                           // 더티체킹
        }

        return entity.toDomain();                           // 엔티티 -> 도메인
    }

    @Override
    public List<Party> findRealtimeByStatus(PartyStatus status) {
        return partyJpaRepository.findAllByStatusAndRoomType(status, RoomType.REALTIME).stream()
                .map(PartyEntity::toDomain).toList();
    }

    @Override
    public boolean existsOngoingByMemberId(Long memberId) {
        return partyJpaRepository.existsByMemberIdAndStatusIn(memberId, openStatuses());
    }

    @Override
    public boolean existsOngoingRealtimeByMemberId(Long memberId) {
        return partyJpaRepository.existsByMemberIdAndRoomTypeAndStatusIn(memberId, RoomType.REALTIME, openStatuses());
    }

    @Override
    public boolean existsOngoingReservationBetween(Long memberId, Instant from, Instant to) {
        return partyJpaRepository.existsReservationBetween(memberId, openStatuses(), from, to);
    }

    @Override
    public void lockMember(Long memberId) {
        partyJpaRepository.lockMember(memberId);
    }

    @Override
    public List<ReservationSummary> findReservations(Instant from, Instant to, int limit) {
        return partyJpaRepository.findReservations(PartyStatus.ACTIVE, from, to, Limit.of(limit));
    }

    @Override
    public List<ReservationSummary> findReservationsWithinBounds(Instant from, Instant to, double swLat, double neLat, double swLng, double neLng, int limit) {
        return partyJpaRepository.findReservationsWithinBounds(PartyStatus.ACTIVE, from, to, swLat, neLat, swLng, neLng, Limit.of(limit));
    }

    /** PostGIS 조건·순서·상한은 native 쿼리가 정해 id 를 고르고, 요약은 예약 목록과 같은 select 로 읽어 고른 순서대로 되돌린다 */
    @Override
    public List<NearbyReservation> findReservationsWithinRadius(Instant from, Instant to, Location departure, Location destination, int radius, int limit) {
        List<ReservationSearchRow> rows = destination == null
                ? partyJpaRepository.findReservationIdsNearDeparture(departure.latitude(), departure.longitude(), from, to, radius, limit).stream()
                        .map(id -> new ReservationSearchRow(id, null))
                        .toList()
                : partyJpaRepository.findReservationIdsNearRoute(departure.latitude(), departure.longitude(),
                        destination.latitude(), destination.longitude(), from, to, radius, limit);

        if(rows.isEmpty()) return List.of();

        Map<Long, ReservationSummary> summaries = partyJpaRepository.findReservationSummariesByIds(rows.stream().map(ReservationSearchRow::id).toList()).stream()
                .collect(Collectors.toMap(ReservationSummary::id, Function.identity()));

        return rows.stream()
                .filter(row -> summaries.containsKey(row.id()))
                .map(row -> new NearbyReservation(summaries.get(row.id()), row.destinationNearby()))
                .toList();
    }

    @Override
    public List<Long> findRecruitingExpired(Instant realtimeCreatedBefore, Instant now) {
        return partyJpaRepository.findRecruitingExpiredIds(PartyStatus.ACTIVE, realtimeCreatedBefore, now);
    }

	@Override
	public List<Long> findOpenConfirmedIdsDepartingBetween(Instant from, Instant to) {
		return partyJpaRepository.findOpenConfirmedIdsDepartingBetween(from, to);
	}

	@Override
	public List<Long> findOpenReservationIdsDepartingBetween(Instant from, Instant to) {
		return partyJpaRepository.findOpenReservationIdsDepartingBetween(from, to);
	}

	private List<PartyStatus> openStatuses() {
        return Arrays.stream(PartyStatus.values())
                .filter(PartyStatus::isOpen)
                .toList();
    }

    @Override
    public Optional<Party> findByIdForUpdate(Long id) {
        return partyJpaRepository.findByForUpdate(id)
                .map(PartyEntity::toDomain);
    }

    @Override
    public List<PartySummary> findSummariesWithinBounds(PartyStatus status, double swLat, double neLat, double swLng, double neLng, int limit) {
        return partyJpaRepository.findSummariesWithinBounds(status, swLat, neLat, swLng, neLng, Limit.of(limit));
    }

    @Override
    public Map<Long, Integer> countFinishedRides(List<Long> memberIds) {
        if(memberIds.isEmpty()) return Map.of();

        Map<Long, Integer> counts = new HashMap<>();

        for(MemberRideCount row : partyJpaRepository.countByMemberIdsAndStatus(memberIds, PartyStatus.FINISHED)) {
            counts.put(row.getMemberId(), row.getCount().intValue());
        }

        return counts;
    }

    @Override
    public List<Long> findAutoCloseTargets(Instant cutoff) {
        return partyJpaRepository.findAutoCloseTargetIds(openStatuses(), cutoff);
    }

    @Override
    public List<NearbyPartySummary> findMatchesWithinRadius(double departLat, double departLng, double destLat, double destLng, int radius) {
        return partyJpaRepository.findNearbyParties(departLat, departLng, destLat, destLng, radius);
    }

    @Override
    public boolean isNearDeparture(Long partyId, double latitude, double longitude, double radiusMeters) {
        return partyJpaRepository.isNearDeparture(partyId, latitude, longitude, radiusMeters);
    }

    @Override
    public Map<Long, PartyPlaces> findPlaces(Collection<Long> ids) {
        if(ids.isEmpty()) return Map.of();

        return partyJpaRepository.findPlacesByIds(ids).stream()
                .collect(Collectors.toMap(PartyPlaceRow::id, row -> new PartyPlaces(row.departure(), row.destination())));
    }
}
