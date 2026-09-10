package team.codingforest.moyeota.chat.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.chat.infrastructure.entity.ChatRoomUserEntity;
import team.codingforest.moyeota.chat.infrastructure.entity.ChatRoomUserId;

import java.util.List;
import java.util.Optional;

public interface ChatRoomUserJpaRepository extends JpaRepository<ChatRoomUserEntity, ChatRoomUserId> {
    List<ChatRoomUserEntity> findByUserIdAndLeftAtIsNull(Long userId);

    Optional<ChatRoomUserEntity> findByUserIdAndChatRoomIdAndLeftAtIsNull(Long userId, Long chatRoomId);

    List<ChatRoomUserEntity> findAllByChatRoomId(Long chatRoomId);
}
