package team.codingforest.moyeota.chat.member.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.chat.member.infrastructure.entity.ChatRoomUserEntity;
import team.codingforest.moyeota.chat.member.infrastructure.entity.ChatRoomUserId;

import java.util.List;
import java.util.Optional;

public interface ChatRoomUserJpaRepository extends JpaRepository<ChatRoomUserEntity, ChatRoomUserId> {
    List<ChatRoomUserEntity> findByUserIdAndLeftAtIsNull(Long userId);

    Optional<ChatRoomUserEntity> findByUserIdAndChatRoomIdAndLeftAtIsNull(Long userId, Long chatRoomId);

    List<ChatRoomUserEntity> findAllByChatRoomIdOrderByCreatedAtAscUserIdAsc(Long chatRoomId);

    List<ChatRoomUserEntity> findAllByChatRoomIdInOrderByChatRoomIdAscCreatedAtAscUserIdAsc(List<Long> chatRoomId);

    @Modifying
    @Query(value = """
        UPDATE chat_room_user c
           SET last_read_message_id = v.message_id,
               updated_at = now()
          FROM unnest(CAST(:chatRoomIds AS bigint[]),
                      CAST(:userIds AS bigint[]),
                      CAST(:messageIds AS bigint[])) AS v(chat_room_id, user_id, message_id)
         WHERE c.chat_room_id = v.chat_room_id
           AND c.user_id = v.user_id
           AND c.left_at IS NULL
           AND (c.last_read_message_id IS NULL OR c.last_read_message_id < v.message_id)
        """, nativeQuery = true)
    int advanceReadPositions(@Param("chatRoomIds") Long[] chatRoomIds,
                             @Param("userIds") Long[] userIds,
                             @Param("messageIds") Long[] messageIds);
}
