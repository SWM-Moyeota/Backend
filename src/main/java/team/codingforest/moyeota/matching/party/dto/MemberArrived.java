package team.codingforest.moyeota.matching.party.dto;

import java.util.UUID;

/** SSE member-arrived - 멤버가 출발지에 도착했다. publicId 는 도착한 사람(유저 정보가 없으면 null) */
public record MemberArrived(UUID publicId, long version) {
    public static MemberArrived from(UUID publicId, long version) {
        return new MemberArrived(publicId, version);
    }
}
