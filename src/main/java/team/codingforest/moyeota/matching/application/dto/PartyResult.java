package team.codingforest.moyeota.matching.application.dto;

import team.codingforest.moyeota.matching.domain.Party;

import java.time.Instant;

public record PartyResult(Long id, Long hostId, Double departureLat, Double departureLng,
                          Double destinationLat, Double destinationLng, String departure, String destination,
                          Integer capacity, Integer currentMembers, Integer departureRadius, Integer destinationRadius,
                          String status, Instant createdAt) {

    public static PartyResult from(Party party) {
        return new PartyResult(
                party.getId(),
                party.getHostId(),
                party.getDepartureLocation().latitude(),
                party.getDepartureLocation().longitude(),
                party.getDestinationLocation().latitude(),
                party.getDestinationLocation().longitude(),
                party.getDeparture(),
                party.getDestination(),
                party.getCapacity().value(),
                party.getMembers().size(),
                party.getDepartureRadius().meters(),
                party.getDestinationRadius().meters(),
                party.getStatus().name(),
                party.getCreatedAt());
    }
}
