package team.codingforest.moyeota.chat.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ChatRooms {
    ChatRoom save(ChatRoom chatRoom);

    Optional<ChatRoom> findById(Long chatRoomId);

    Map<Long, ChatRoom> findByIds(List<Long> chatRoomIds);

    boolean existsByPartyId(Long partyId);

    Optional<ChatRoom> findByPartyId(Long partyId);
}