package team.codingforest.moyeota.matching.application.dto;

import team.codingforest.moyeota.matching.domain.Party;

import java.util.List;

public record PartyListResponse(List<PartyItem> list) {

    public record PartyItem(Long partyId, String departure, String destination, Integer currentMembers, Integer capacity,
                            String status) {
        static PartyItem from(PartyResult r) {
            return new PartyItem(r.id(), r.departure(), r.destination(), r.currentMembers(), r.capacity(), r.status());
        }
    }

    public static PartyListResponse from(List<PartyResult> results) {
        return new PartyListResponse(results.stream().map(PartyItem::from).toList());
    }
}
