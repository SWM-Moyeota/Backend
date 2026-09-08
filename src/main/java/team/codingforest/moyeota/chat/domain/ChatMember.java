package team.codingforest.moyeota.chat.domain;

import java.util.UUID;

public record ChatMember(Long userId, UUID publicId, String nickname, String imageUrl) {}