package team.codingforest.moyeota.chat.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomUserEntity;
import team.codingforest.moyeota.chat.infra.entity.ChatRoomUserId;

import java.util.List;

public interface ChatRoomUserJpaRepository extends JpaRepository<ChatRoomUserEntity, ChatRoomUserId> {

    // 안 나간 방 조회
    List<ChatRoomUserEntity> findByUserIdAndLeftAtIsNull(Long userId);

}
