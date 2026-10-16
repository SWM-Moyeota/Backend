package team.codingforest.moyeota.matching.party.dto;

public record PartyClosed(Long partyId, String status, long version) {
    public static PartyClosed from(Long partyId, String status, long version) {
        return new PartyClosed(partyId, status, version);
    }
}
