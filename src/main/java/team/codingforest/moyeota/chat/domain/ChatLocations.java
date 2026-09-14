package team.codingforest.moyeota.chat.domain;

import java.util.Map;

public interface ChatLocations {
    void startSession(Long userId, Long chatRoomId);
    void put(Long userId, Long chatRoomId, ChatLocation chatLocation);
    Map<Long, ChatLocation> findAll(Long chatRoomId);
    boolean isSharing(Long userId, Long chatRoomId);
    void stop(Long userId, Long chatRoomId);
}
