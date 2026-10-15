package team.codingforest.moyeota.chat.notification.domain;

import java.util.UUID;

public record ChatMessageNotification(
        Long chatRoomId,
        Long messageId,
        Long seq,
        UUID senderPublicId,
        String senderNickname,
        String preview
) {}