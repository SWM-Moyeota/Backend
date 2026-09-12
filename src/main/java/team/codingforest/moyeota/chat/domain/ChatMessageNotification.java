package team.codingforest.moyeota.chat.domain;

import java.util.UUID;

public record ChatMessageNotification(
        Long chatRoomId,
        Long messageId,
        UUID senderPublicId,
        String senderNickname,
        String preview
) {}