package team.codingforest.moyeota.matching.party.dto;

public record MemberJoined(PartyDetailResult.MemberInfo member, String status) {

    public static MemberJoined from(PartyDetailResult.MemberInfo member, String status) {
        return new MemberJoined(member, status);
    }
}
