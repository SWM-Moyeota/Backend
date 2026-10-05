package team.codingforest.moyeota.matching.party.dto;

import team.codingforest.moyeota.matching.party.domain.PartyStatusSnapshot;

public record PartyStatusResponse(String status, Integer currentMembers) {

    public static PartyStatusResponse from(PartyStatusSnapshot snapshot) {
        return new PartyStatusResponse(snapshot.status().name(), snapshot.currentMembers().intValue());
    }
}
