package team.codingforest.moyeota.matching.party.dto;

public record PartyClosed(String status) {
    public static PartyClosed from(String status) {
        return new PartyClosed(status);
    }
}
