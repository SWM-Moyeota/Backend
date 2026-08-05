package team.codingforest.moyeota.chat.infra;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.chat.infra.entity.ChatMessageEntity;

import java.util.List;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Limit limit);

    List<ChatMessageEntity> findByChatRoomIdAndIdLessThanOrderByIdDesc(Long chatRoomId, Long cursor, Limit limit);

    List<ChatMessageEntity> findByChatRoomIdAndIdGreaterThanOrderByIdAsc(Long chatRoomId, Long cursor, Limit limit);
}