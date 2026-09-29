package team.codingforest.moyeota.chat.member.domain;

import java.util.UUID;

public record ChatMember(Long userId, UUID publicId, String nickname, String imageUrl) {}