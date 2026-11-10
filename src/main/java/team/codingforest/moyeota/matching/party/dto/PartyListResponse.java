package team.codingforest.moyeota.matching.party.dto;

import team.codingforest.moyeota.matching.party.domain.PartySummary;

import java.util.List;

public record PartyListResponse(List<PartyItem> list) {

    public record PartyItem(Long partyId, String departure, String destination, Integer currentMembers, Integer capacity,
                            String status, Double departureLat, Double departureLng) {
    }

    public static PartyListResponse from(List<PartySummary> summaries) {
        return new PartyListResponse(summaries.stream()
                .map(s -> new PartyItem(s.id(), s.departure(), s.destination(), s.currentMembers().intValue(), s.capacity(),
                        s.status().name(), s.departureLat(), s.departureLng()))
                .toList());
    }
}
