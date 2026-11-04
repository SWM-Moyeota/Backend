package team.codingforest.moyeota.matching.party.dto;

public record RecruitingClosed(String status, long version) {
    public static RecruitingClosed from(String status, long version) {
        return new RecruitingClosed(status, version);
    }
}
