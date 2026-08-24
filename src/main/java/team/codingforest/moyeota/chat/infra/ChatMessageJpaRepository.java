package team.codingforest.moyeota.chat.infra;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.chat.domain.ChatMessageStatus;
import team.codingforest.moyeota.chat.infra.entity.ChatMessageEntity;

import java.util.List;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Limit limit);

    List<ChatMessageEntity> findByChatRoomIdAndIdLessThanOrderByIdDesc(Long chatRoomId, Long cursor, Limit limit);

    List<ChatMessageEntity> findByChatRoomIdAndIdGreaterThanOrderByIdAsc(Long chatRoomId, Long cursor, Limit limit);

    @Query("""
            SELECT m FROM ChatMessageEntity m
            WHERE m.chatRoomId = :chatRoomId
            AND m.status = :status
            AND m.content LIKE %:keyword%
            AND (:cursor IS NULL OR m.id < :cursor)
            ORDER BY m.id DESC
            """)
    List<ChatMessageEntity> search(
            @Param("chatRoomId") Long chatRoomId, @Param("status") ChatMessageStatus status,
            @Param("keyword") String keyword, @Param("cursor") Long cursor, Limit limit
    );
}