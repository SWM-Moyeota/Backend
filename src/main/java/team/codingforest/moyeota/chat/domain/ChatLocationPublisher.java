package team.codingforest.moyeota.chat.domain;

import java.util.UUID;

public interface ChatLocationPublisher {
    void publish(Long chatRoomId, UUID publicId, ChatLocation location);
}