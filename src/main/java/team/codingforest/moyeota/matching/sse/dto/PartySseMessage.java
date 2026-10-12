package team.codingforest.moyeota.matching.sse.dto;

public record PartySseMessage(Long partyId, String event, String data, Long leftMemberId) {
    public static PartySseMessage from(Long partyId, String event, String data, Long leftMemberId) {
        return new PartySseMessage(partyId, event, data, leftMemberId);
    }
}
