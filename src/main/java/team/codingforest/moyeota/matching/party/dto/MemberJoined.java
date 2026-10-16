package team.codingforest.moyeota.matching.party.dto;

public record MemberJoined(PartyDetailResult.MemberInfo member, String status, long version) {

    public static MemberJoined from(PartyDetailResult.MemberInfo member, String status, long version) {
        return new MemberJoined(member, status, version);
    }
}
