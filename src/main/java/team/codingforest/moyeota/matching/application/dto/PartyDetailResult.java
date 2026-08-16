package team.codingforest.moyeota.matching.application.dto;

import team.codingforest.moyeota.matching.domain.Party;

import java.time.Instant;
import java.util.List;

public record PartyDetailResult(Long id, Long hostId,
                               Double departureLat, Double departureLng,
                               Double destinationLat, Double destinationLng,
                               String departure, String destination,
                               Integer capacity, Integer currentMembers,
                               Integer departureRadius, Integer destinationRadius,
                               String status, Instant createdAt,
                               List<MemberInfo> members) {

    public record MemberInfo(Long memberId, boolean isHost, Instant joinedAt) {}

    public static PartyDetailResult from(Party party) {
        List<MemberInfo> members = party.getMembers().stream()
                .map(m -> new MemberInfo(m.getMemberId(), m.getMemberId().equals(party.getHostId()),
                        m.getJoinedAt())).toList();

        return new PartyDetailResult(
                party.getId(), party.getHostId(), party.getDepartureLocation().latitude(),
                party.getDepartureLocation().longitude(),
                party.getDestinationLocation().latitude(), party.getDestinationLocation().longitude(),
                party.getDeparture(), party.getDestination(), party.getCapacity().value(),
                party.getMembers().size(), party.getDepartureRadius().meters(), party.getDestinationRadius().meters(),
                party.getStatus().name(), party.getCreatedAt(), members
        );
    }
}
