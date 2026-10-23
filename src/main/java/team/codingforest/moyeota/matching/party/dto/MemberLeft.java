package team.codingforest.moyeota.matching.party.dto;

import java.util.UUID;

public record MemberLeft(UUID publicId, String status, UUID hostPublicId, long version) {
    public static MemberLeft from(UUID publicId, String status, UUID hostPublicId, long version) {
        return new MemberLeft(publicId, status, hostPublicId, version);
    }
}
