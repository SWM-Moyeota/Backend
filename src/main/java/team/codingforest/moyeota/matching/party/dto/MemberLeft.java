package team.codingforest.moyeota.matching.party.dto;

import java.util.UUID;

public record MemberLeft(UUID publicId, String status) {
    public static MemberLeft from(UUID publicId, String status) {
        return new MemberLeft(publicId, status);
    }
}
