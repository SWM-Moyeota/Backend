package team.codingforest.moyeota.chat.domain;

import java.util.Optional;

public interface ChatRoomRepository {
    ChatRoom save(ChatRoom chatRoom);
    Optional<ChatRoom> findById(Long chatRoomId);
    boolean existsByPartyId(Long partyId);
    Optional<ChatRoom> findByPartyId(Long partyId);
}
