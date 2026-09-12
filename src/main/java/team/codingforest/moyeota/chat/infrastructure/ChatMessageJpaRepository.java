package team.codingforest.moyeota.chat.infrastructure;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.chat.domain.ChatMessageStatus;
import team.codingforest.moyeota.chat.infrastructure.entity.ChatMessageEntity;

import java.util.List;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByChatRoomIdOrderByIdDesc(Long chatRoomId, Limit limit);

    List<ChatMessageEntity> findByChatRoomIdAndIdLessThanOrderByIdDesc(Long chatRoomId, Long cursor, Limit limit);

    List<ChatMessageEntity> findByChatRoomIdAndIdGreaterThanOrderByIdAsc(Long chatRoomId, Long cursor, Limit limit);

    @Query("""
            SELECT m FROM ChatMessageEntity m
            WHERE m.chatRoomId = :chatRoomId
            AND m.status = :status
            AND m.content LIKE %:keyword% ESCAPE '!'
            AND (:cursor IS NULL OR m.id < :cursor)
            ORDER BY m.id DESC
            """)
    List<ChatMessageEntity> search(
            @Param("chatRoomId") Long chatRoomId, @Param("status") ChatMessageStatus status,
            @Param("keyword") String keyword, @Param("cursor") Long cursor, Limit limit
    );

    @Query("""
            SELECT m FROM ChatMessageEntity m
            WHERE m.id IN (
                SELECT MAX(m2.id) FROM ChatMessageEntity m2
                WHERE m2.chatRoomId IN :chatRoomIds
                GROUP BY m2.chatRoomId
            )
        """)
    List<ChatMessageEntity> findLatestByChatRoomIds(@Param("chatRoomIds") List<Long> chatRoomIds);

    @Query("""
            SELECT m.chatRoomId AS chatRoomId, COUNT(m) AS unreadCount
            FROM ChatMessageEntity m, ChatRoomUserEntity cru
            WHERE cru.chatRoomId = m.chatRoomId
              AND cru.userId = :userId
              AND cru.leftAt IS NULL
              AND m.userId <> :userId
              AND m.status = :status
              AND m.id > COALESCE(cru.lastReadMessageId, 0)
            GROUP BY m.chatRoomId
            """)
    List<UnreadCount> countUnreadByUserId(
            @Param("userId") Long userId, @Param("status") ChatMessageStatus status);

    interface UnreadCount {
        Long getChatRoomId();
        Long getUnreadCount();
    }
}