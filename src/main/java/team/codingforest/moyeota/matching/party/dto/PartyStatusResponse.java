package team.codingforest.moyeota.matching.party.dto;

import team.codingforest.moyeota.matching.party.domain.PartyStatusSnapshot;

public record PartyStatusResponse(String status, Integer currentMembers, String fingerprint) {

    public static PartyStatusResponse of(PartyStatusSnapshot snapshot, String fingerprint) {
        return new PartyStatusResponse(snapshot.status().name(), snapshot.memberIds().size(), fingerprint);
    }
}
