package team.codingforest.moyeota.chat.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomUserEntity;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomUserId;

import java.util.List;
import java.util.Optional;

public interface ChatRoomUserJpaRepository extends JpaRepository<ChatRoomUserEntity, ChatRoomUserId> {
    List<ChatRoomUserEntity> findByUserIdAndLeftAtIsNull(Long userId);
    Optional<ChatRoomUserEntity> findByUserIdAndChatRoomId(Long userId, Long chatRoomId);

}
