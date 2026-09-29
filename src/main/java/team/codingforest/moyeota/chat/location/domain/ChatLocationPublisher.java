package team.codingforest.moyeota.chat.location.domain;

import java.util.UUID;

public interface ChatLocationPublisher {
    void publish(Long chatRoomId, UUID publicId, ChatLocation location);
}