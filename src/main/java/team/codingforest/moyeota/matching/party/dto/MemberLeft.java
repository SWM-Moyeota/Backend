package team.codingforest.moyeota.matching.party.dto;

import java.util.UUID;

public record MemberLeft(UUID publicId, String status, UUID hostPublicId) {
    public static MemberLeft from(UUID publicId, String status, UUID hostPublicId) {
        return new MemberLeft(publicId, status, hostPublicId);
    }
}
