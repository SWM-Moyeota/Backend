package team.codingforest.moyeota.chat.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomEntity;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomEntity, Long> {
    boolean existsByPartyId(Long partyId);
}
