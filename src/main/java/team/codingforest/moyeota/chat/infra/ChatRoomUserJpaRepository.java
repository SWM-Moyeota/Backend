package team.codingforest.moyeota.chat.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomUserEntity;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomUserId;

public interface ChatRoomUserJpaRepository extends JpaRepository<ChatRoomUserEntity, ChatRoomUserId> {
}
