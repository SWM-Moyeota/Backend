package team.codingforest.moyeota.chat.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;
import team.codingforest.moyeota.chat.domain.ChatRoomUsers;
import team.codingforest.moyeota.chat.infrastructure.entity.ChatRoomUserEntity;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatRoomUserJpa implements ChatRoomUsers {

    private final ChatRoomUserJpaRepository jpaRepository;

    @Override
    public ChatRoomUser save(ChatRoomUser chatRoomUser) {
        ChatRoomUserEntity entity = ChatRoomUserEntity.from(chatRoomUser);
        ChatRoomUserEntity saved = jpaRepository.saveAndFlush(entity);
        return saved.toDomain();
    }

    @Override
    public List<ChatRoomUser> findActiveByUserId(Long userId) {
        List<ChatRoomUserEntity> entities = jpaRepository.findByUserIdAndLeftAtIsNull(userId);
        return entities.stream()
                .map(ChatRoomUserEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<ChatRoomUser> findActiveByUserIdAndChatRoomId(Long userId, Long chatRoomId) {
        Optional<ChatRoomUserEntity> entity = jpaRepository.findByUserIdAndChatRoomIdAndLeftAtIsNull(userId, chatRoomId);
        return entity.map(ChatRoomUserEntity::toDomain);
    }

    @Override
    public List<ChatRoomUser> findAllByChatRoomId(Long chatRoomId) {
        return jpaRepository.findAllByChatRoomIdOrderByCreatedAtAscUserIdAsc(chatRoomId).stream()
                .map(ChatRoomUserEntity::toDomain)
                .toList();
    }
}
