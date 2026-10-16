package team.codingforest.moyeota.matching.sse.dto;

public record PartySseMessage(Long partyId, String event, String data, Long leftMemberId, Long publishedAt) {
    public static PartySseMessage from(Long partyId, String event, String data, Long leftMemberId, Long publishedAt) {
        return new PartySseMessage(partyId, event, data, leftMemberId, publishedAt);
    }
}
