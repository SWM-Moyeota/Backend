package team.codingforest.moyeota.chat.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomEntity;

import java.util.Optional;

public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomEntity, Long> {
    Optional<ChatRoomEntity> findByPartyId(Long partyId);
    boolean existsByPartyId(Long partyId);
}
