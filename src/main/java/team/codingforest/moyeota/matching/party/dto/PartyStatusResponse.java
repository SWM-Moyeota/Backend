package team.codingforest.moyeota.matching.party.dto;

import team.codingforest.moyeota.matching.party.domain.Party;

public record PartyStatusResponse(String status, Integer currentMembers) {

    public static PartyStatusResponse from(Party party) {
        return new PartyStatusResponse(party.getStatus().name(), party.getMembers().size());
    }
}
